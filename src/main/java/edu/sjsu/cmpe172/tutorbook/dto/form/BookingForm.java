package edu.sjsu.cmpe172.tutorbook.dto.form;

import jakarta.validation.constraints.Size;

/** The booking form's input. The slot comes from the URL and the customer from the session. */
public class BookingForm {

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    /** Blank notes are stored as NULL. */
    public String notesOrNull() {
        return (notes == null || notes.isBlank()) ? null : notes.strip();
    }
}
