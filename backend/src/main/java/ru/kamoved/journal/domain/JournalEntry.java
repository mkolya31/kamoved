package ru.kamoved.journal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;
import ru.kamoved.auth.domain.AppUser;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "journal_entry")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntryType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 50)
    private ExecutionStatus executionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "prepayment_amount", precision = 14, scale = 2)
    private BigDecimal prepaymentAmount;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_method", length = 40)
    private FulfillmentMethod fulfillmentMethod;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    private String comment;

    @Column(name = "search_text", nullable = false, columnDefinition = "text")
    private String searchText = "";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 30)
    private List<JournalEntryItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 30)
    private List<EntryContact> contacts = new ArrayList<>();

    protected JournalEntry() {
    }

    private JournalEntry(AppUser createdBy) {
        this.type = EntryType.SALE;
        this.executionStatus = ExecutionStatus.COMPLETED;
        this.paymentStatus = PaymentStatus.PAID;
        this.totalAmount = BigDecimal.ZERO;
        this.createdBy = createdBy;
    }

    public static JournalEntry sale(AppUser createdBy, String comment) {
        JournalEntry entry = new JournalEntry(createdBy);
        entry.comment = comment;
        return entry;
    }

    public static JournalEntry order(
        AppUser createdBy,
        ExecutionStatus executionStatus,
        PaymentStatus paymentStatus,
        BigDecimal prepaymentAmount,
        FulfillmentMethod fulfillmentMethod,
        String deliveryAddress,
        String comment
    ) {
        JournalEntry entry = new JournalEntry(createdBy);
        entry.type = EntryType.ORDER;
        entry.executionStatus = executionStatus;
        entry.paymentStatus = paymentStatus;
        entry.prepaymentAmount = prepaymentAmount;
        entry.fulfillmentMethod = fulfillmentMethod;
        entry.deliveryAddress = deliveryAddress;
        entry.comment = comment;
        return entry;
    }

    public void addItem(JournalEntryItem item) {
        items.add(item);
        item.attachTo(this);
    }

    public void addContact(EntryContact contact) {
        contacts.add(contact);
        contact.attachTo(this);
    }

    public void replaceItems(List<JournalEntryItem> newItems) {
        items.clear();
        newItems.forEach(this::addItem);
    }

    public void replaceContacts(List<EntryContact> newContacts) {
        contacts.clear();
        newContacts.forEach(this::addContact);
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void changeExecutionStatus(ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }

    public void changePayment(PaymentStatus paymentStatus, BigDecimal prepaymentAmount) {
        this.paymentStatus = paymentStatus;
        this.prepaymentAmount = prepaymentAmount;
    }

    public void changeFulfillment(
        FulfillmentMethod fulfillmentMethod,
        String deliveryAddress
    ) {
        this.fulfillmentMethod = fulfillmentMethod;
        this.deliveryAddress = deliveryAddress;
    }

    public void changeComment(String comment) {
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        refreshSearchText();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        refreshSearchText();
        updatedAt = OffsetDateTime.now();
    }

    public void refreshSearchText() {
        List<String> values = new ArrayList<>();
        values.add(JournalSearchNormalizer.normalizeText(deliveryAddress));
        contacts.forEach(contact -> {
            values.add(JournalSearchNormalizer.normalizeText(contact.getName()));
            String phone = JournalSearchNormalizer.normalizePhone(contact.getPhone());
            values.add(phone == null ? "" : phone);
        });
        items.forEach(item -> values.add(JournalSearchNormalizer.normalizeText(item.getName())));
        searchText = String.join(" ", values).trim().replaceAll(" +", " ");
    }

    public Long getId() {
        return id;
    }

    public EntryType getType() {
        return type;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public BigDecimal getPrepaymentAmount() {
        return prepaymentAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public FulfillmentMethod getFulfillmentMethod() {
        return fulfillmentMethod;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getComment() {
        return comment;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<JournalEntryItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<EntryContact> getContacts() {
        return Collections.unmodifiableList(contacts);
    }
}
