package com.biashara.finance.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.iam.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A double-entry journal posting. Debits must equal credits before it can post. */
@Entity
@Table(name = "journal_entries")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class JournalEntry extends TenantAwareEntity {

    @Column(nullable = false)
    private String entryNumber;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false)
    private String description;

    /** Source document that produced this entry, e.g. "INV-000042". */
    private String reference;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalDebit;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalCredit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Builder.Default
    @Column(nullable = false)
    private boolean posted = false;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<JournalLine> lines = new ArrayList<>();

    public void addLine(JournalLine line) {
        line.setEntry(this);
        lines.add(line);
    }

    public boolean isBalanced() {
        if (totalDebit == null || totalCredit == null) {
            return false;
        }
        return totalDebit.compareTo(totalCredit) == 0;
    }
}
