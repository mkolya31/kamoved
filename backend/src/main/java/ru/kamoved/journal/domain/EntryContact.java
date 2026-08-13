package ru.kamoved.journal.domain;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "entry_contact")
public class EntryContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactType type;

    @Column(length = 255)
    private String name;

    @Column(length = 100)
    private String phone;

    @Column(name = "normalized_phone", length = 30)
    private String normalizedPhone;

    private String comment;

    protected EntryContact() {
    }

    public EntryContact(
        ContactType type,
        String name,
        String phone,
        String normalizedPhone,
        String comment
    ) {
        this.type = type;
        this.name = name;
        this.phone = phone;
        this.normalizedPhone = normalizedPhone;
        this.comment = comment;
    }

    void attachTo(JournalEntry journalEntry) {
        this.journalEntry = journalEntry;
    }

    public Long getId() {
        return id;
    }

    public ContactType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getNormalizedPhone() {
        return normalizedPhone;
    }

    public String getComment() {
        return comment;
    }
}
