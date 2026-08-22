package ru.kamoved.journal.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.kamoved.journal.api.dto.JournalPageResponse;
import ru.kamoved.journal.api.dto.JournalEntryDetails;
import ru.kamoved.journal.application.JournalQueryService;

@Validated
@RestController
@RequestMapping("/api/journal")
public class JournalController {

    private final JournalQueryService journalQueryService;

    public JournalController(JournalQueryService journalQueryService) {
        this.journalQueryService = journalQueryService;
    }

    @GetMapping
    JournalPageResponse list(
        @RequestParam(defaultValue = "all")
        @Pattern(regexp = "all|active") String mode,

        @RequestParam(defaultValue = "0")
        @Min(0) int page,

        @RequestParam(defaultValue = "30")
        @Min(1) @Max(100) int size
    ) {
        return journalQueryService.list(mode, page, size);
    }

    @GetMapping("/{id}")
    JournalEntryDetails get(@PathVariable @Min(1) long id) {
        return journalQueryService.get(id);
    }

    @GetMapping("/search")
    JournalPageResponse search(
        @RequestParam @Size(max = 200) String query,

        @RequestParam(defaultValue = "all")
        @Pattern(regexp = "all|active") String mode,

        @RequestParam(defaultValue = "0")
        @Min(0) int page,

        @RequestParam(defaultValue = "30")
        @Min(1) @Max(30) int size
    ) {
        return journalQueryService.search(query, mode, page, size);
    }
}
