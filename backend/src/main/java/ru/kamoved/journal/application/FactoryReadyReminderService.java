package ru.kamoved.journal.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import ru.kamoved.auth.domain.AppUser;
import ru.kamoved.journal.domain.ContactType;
import ru.kamoved.journal.domain.EntryContact;
import ru.kamoved.journal.domain.JournalEntry;
import ru.kamoved.journal.domain.JournalEntryItem;
import ru.kamoved.journal.persistence.JournalEntryRepository;
import ru.kamoved.notification.application.EmailNotificationRecipients;
import ru.kamoved.notification.application.EmailNotificationService;
import ru.kamoved.notification.application.EnqueueEmailNotificationCommand;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class FactoryReadyReminderService {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Moscow");
    static final LocalTime REMINDER_TIME = LocalTime.of(9, 0);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final JournalEntryRepository entries;
    private final EmailNotificationRecipients recipients;
    private final EmailNotificationService notifications;
    private final FactoryReadyEmailNotificationType notificationType;
    private final Clock clock;

    public FactoryReadyReminderService(
        JournalEntryRepository entries,
        EmailNotificationRecipients recipients,
        EmailNotificationService notifications,
        FactoryReadyEmailNotificationType notificationType,
        Clock clock
    ) {
        this.entries = entries;
        this.recipients = recipients;
        this.notifications = notifications;
        this.notificationType = notificationType;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${kamoved.factory-ready.scan-delay-ms:30000}",
        fixedDelayString = "${kamoved.factory-ready.scan-delay-ms:60000}")
    @Transactional
    public void processDueReminders() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(BUSINESS_ZONE));
        LocalDate today = now.toLocalDate();
        for (JournalEntry order : entries.findAllByFactoryReadyDateIsNotNull()) {
            boolean wasActive = order.isFactoryReadyAttention();
            LocalDate plannedActivationDate = order.getFactoryReadyDate().minusDays(2);
            boolean activationDue = today.isAfter(plannedActivationDate)
                || (today.equals(plannedActivationDate)
                    && !now.toLocalTime().isBefore(REMINDER_TIME));
            if (activationDue) {
                order.activateFactoryReadyAttentionIfDue(today);
            }
            if (!wasActive && order.isFactoryReadyAttention()) {
                entries.save(order);
            }
            if (!order.isFactoryReadyAttention()
                || today.isBefore(order.getFactoryReadyReminderStartDate())
                || (today.equals(order.getFactoryReadyReminderStartDate())
                    && now.toLocalTime().isBefore(REMINDER_TIME))) {
                continue;
            }
            OffsetDateTime scheduledAt = now.toLocalTime().isBefore(REMINDER_TIME)
                ? now.toOffsetDateTime()
                : today.atTime(REMINDER_TIME).atZone(BUSINESS_ZONE).toOffsetDateTime();
            for (AppUser recipient : recipients.findActiveRecipients(notificationType)) {
                enqueue(order, recipient, today, scheduledAt);
            }
        }
    }

    private void enqueue(
        JournalEntry order,
        AppUser recipient,
        LocalDate today,
        OffsetDateTime scheduledAt
    ) {
        String key = "%s:%d:%s:%s:%s".formatted(
            FactoryReadyEmailNotificationType.CODE,
            order.getId(), order.getFactoryReadyDate(), today, recipientAddressKey(recipient));
        String subject = "Заказ З-%d: проверить готовность на заводе до %s".formatted(
            order.getId(), SHORT_DATE.format(order.getFactoryReadyDate()));
        notifications.enqueue(new EnqueueEmailNotificationCommand(
            key,
            recipient.getUsername(),
            scheduledAt,
            subject,
            textBody(order),
            htmlBody(order)
        ));
    }

    private String textBody(JournalEntry order) {
        StringBuilder body = new StringBuilder()
            .append("Дата готовности: ").append(FULL_DATE.format(order.getFactoryReadyDate()))
            .append(" | Заказ: З-").append(order.getId())
            .append(" | Статус: ").append(executionStatusLabel(order))
            .append("\n\nПОЗИЦИИ ЗАКАЗА\n");
        order.getItems().forEach(item -> body.append("• ").append(item.getName())
            .append(" — ").append(formatQuantity(item)).append(' ')
            .append(unitLabel(item)).append('\n'));
        appendOptionalDetails(body, order);
        return body.toString();
    }

    private String htmlBody(JournalEntry order) {
        StringBuilder items = new StringBuilder();
        for (JournalEntryItem item : order.getItems()) {
            items.append("<tr><td style=\"padding:16px 0;border-bottom:1px solid #e7e1d8;"
                    + "font-size:17px;font-weight:700;line-height:1.35;color:#25211d;\">")
                .append(escape(item.getName()))
                .append("</td><td style=\"padding:16px 0 16px 20px;border-bottom:1px solid #e7e1d8;"
                    + "font-size:16px;font-weight:700;line-height:1.35;color:#25211d;"
                    + "text-align:right;white-space:nowrap;\">")
                .append(formatQuantity(item)).append(' ').append(unitLabel(item))
                .append("</td></tr>");
        }
        StringBuilder footer = new StringBuilder();
        EntryContact client = client(order);
        if (client != null && (client.getName() != null || client.getPhone() != null)) {
            footer.append("<div style=\"margin-bottom:8px;\"><span style=\"font-size:12px;"
                    + "font-weight:700;text-transform:uppercase;letter-spacing:.08em;color:#7a7065;\">"
                    + "Клиент</span><div style=\"margin-top:4px;font-size:15px;line-height:1.5;color:#403a34;\">")
                .append(escape(client.getName() == null ? "" : client.getName()));
            if (client.getPhone() != null) footer.append(" · ").append(escape(client.getPhone()));
            footer.append("</div></div>");
        }
        if (order.getFulfillmentMethod() != null) {
            footer.append("<div style=\"font-size:14px;line-height:1.5;color:#6b6259;\">"
                    + "<strong>Получение:</strong> ")
                .append(fulfillmentLabel(order)).append("</div>");
        }
        String footerBlock = footer.isEmpty() ? "" : "<div style=\"padding:20px 24px;background:#f6f3ee;"
            + "border-top:1px solid #e1dbd2;\">" + footer + "</div>";
        return "<div style=\"margin:0;padding:24px;background:#efede9;font-family:Arial,sans-serif;"
            + "color:#25211d;\"><div style=\"max-width:640px;margin:0 auto;overflow:hidden;"
            + "background:#ffffff;border:1px solid #ded8cf;border-radius:12px;\">"
            + "<table role=\"presentation\" style=\"width:100%;border-collapse:collapse;"
            + "background:#fff3df;border-bottom:3px solid #d97706;\"><tr>"
            + headerCell("Дата готовности", FULL_DATE.format(order.getFactoryReadyDate()))
            + headerCell("Заказ", "З-" + order.getId())
            + headerCell("Статус", executionStatusLabel(order))
            + "</tr></table>"
            + "<div style=\"padding:26px 24px 30px;\"><div style=\"margin-bottom:8px;font-size:12px;"
            + "font-weight:700;text-transform:uppercase;letter-spacing:.1em;color:#b35d08;\">"
            + "Позиции заказа</div><table role=\"presentation\" style=\"width:100%;"
            + "border-collapse:collapse;\">" + items + "</table></div>"
            + footerBlock + "</div></div>";
    }

    private String headerCell(String label, String value) {
        return "<td style=\"width:33.33%;padding:16px 18px;vertical-align:top;\">"
            + "<div style=\"font-size:11px;font-weight:700;text-transform:uppercase;"
            + "letter-spacing:.07em;color:#8b5a26;white-space:nowrap;\">" + escape(label) + "</div>"
            + "<div style=\"margin-top:5px;font-size:15px;font-weight:700;line-height:1.3;"
            + "color:#33271c;\">" + escape(value) + "</div></td>";
    }

    private void appendOptionalDetails(StringBuilder body, JournalEntry order) {
        EntryContact client = client(order);
        if (client != null && (client.getName() != null || client.getPhone() != null)) {
            body.append("\nКлиент: ").append(client.getName() == null ? "" : client.getName());
            if (client.getPhone() != null) body.append(" · ").append(client.getPhone());
            body.append('\n');
        }
        if (order.getFulfillmentMethod() != null) {
            body.append("Получение: ").append(fulfillmentLabel(order)).append('\n');
        }
    }

    private EntryContact client(JournalEntry order) {
        return order.getContacts().stream()
            .filter(contact -> contact.getType() == ContactType.CLIENT).findFirst().orElse(null);
    }

    private String executionStatusLabel(JournalEntry order) {
        return switch (order.getExecutionStatus()) {
            case NEW -> "Новый";
            case ORDERED_FACTORY -> "Заказан на заводе";
            case IN_PRODUCTION -> "В производстве";
            case READY_FACTORY -> "Готов на заводе";
            case IN_TRANSIT_TO_WAREHOUSE -> "В пути на склад";
            case AT_WAREHOUSE -> "На нашем складе";
            case OUT_FOR_DELIVERY -> "В доставке клиенту";
            case COMPLETED -> "Завершён";
            case CANCELLED -> "Отменён";
        };
    }

    private String fulfillmentLabel(JournalEntry order) {
        return switch (order.getFulfillmentMethod()) {
            case PICKUP_WAREHOUSE -> "Самовывоз со склада";
            case PICKUP_FACTORY -> "Самовывоз с завода";
            case DELIVERY_FACTORY -> "Доставка от завода";
            case DELIVERY_MARKET -> "Доставка от рынка";
        };
    }

    private String unitLabel(JournalEntryItem item) {
        return switch (item.getUnit()) {
            case PIECE -> "шт.";
            case SQUARE_METER -> "м²";
            case LINEAR_METER -> "пог. м";
            case PACKAGE -> "уп.";
        };
    }

    private String formatQuantity(JournalEntryItem item) {
        return item.getQuantity().stripTrailingZeros().toPlainString();
    }

    private String escape(String value) { return HtmlUtils.htmlEscape(value); }

    private static String recipientAddressKey(AppUser recipient) {
        String normalizedEmail = recipient.getEmail().trim().toLowerCase(Locale.ROOT);
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(normalizedEmail.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }
}
