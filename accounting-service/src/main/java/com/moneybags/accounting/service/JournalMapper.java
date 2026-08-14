package com.moneybags.accounting.service;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.entity.Journal;
import com.moneybags.accounting.entity.JournalLine;
import org.springframework.stereotype.Component;

@Component
public class JournalMapper {
    public JournalResponse toResponse(Journal journal, boolean replay) {
        return new JournalResponse(journal.getJournalNumber(), journal.getPostingSequence(),
                journal.getExternalReference(), journal.getSourceService(), journal.getEventType(),
                journal.getOccurredAt(), journal.getBusinessDate(), journal.getCurrencyCode(),
                journal.getStatus().name(), journal.getTotalDebit(), journal.getTotalCredit(),
                journal.getCorrelationId(), journal.getPostedAt(), replay, journal.getReversesJournalNumber(),
                journal.getLines().stream().map(this::line).toList());
    }

    private JournalLineResponse line(JournalLine value) {
        return new JournalLineResponse(value.getLineNumber(), value.getGlCode(), value.getSubledgerReference(),
                value.getComponentType(), value.getRuleCode(), value.getRuleVersion(), value.getDebitAmount(),
                value.getCreditAmount(), value.getNarration());
    }
}
