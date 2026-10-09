package edu.sjsu.cmpe172.tutorbook.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import edu.sjsu.cmpe172.tutorbook.dto.form.BookingForm;
import edu.sjsu.cmpe172.tutorbook.dto.form.SlotForm;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Unit tests for the Bean Validation rules on request input. */
class FormValidationTest {

    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void bookingNotesLongerThan500CharsAreRejected() {
        BookingForm form = new BookingForm();
        form.setNotes("x".repeat(501));
        assertThat(validator.validate(form)).hasSize(1);

        form.setNotes("x".repeat(500));
        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void slotFormRequiresServiceDateAndTime() {
        assertThat(validator.validate(new SlotForm()))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("serviceId", "date", "startTime");
    }

    @Test
    void slotDateCannotBeInThePast() {
        SlotForm form = new SlotForm();
        form.setServiceId(1L);
        form.setDate(LocalDate.now().minusDays(1));
        form.setStartTime(LocalTime.NOON);

        assertThat(validator.validate(form))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("date");
    }
}
