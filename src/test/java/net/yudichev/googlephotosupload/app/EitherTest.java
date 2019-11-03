package net.yudichev.googlephotosupload.app;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class EitherTest {
    @Test
    void mapsLeft() {
        assertThat(Either.<String, String>left("Value").map(String::toUpperCase, String::toLowerCase), is("VALUE"));
    }

    @Test
    void mapsRight() {
        assertThat(Either.<String, String>right("Value").map(String::toUpperCase, String::toLowerCase), is("value"));
    }

    @Test
    void mapsLeftNull() {
        assertThat(Either.<String, String>left(null).map(s -> "left", s -> "right"), is("left"));
    }

    @Test
    void mapsRightNull() {
        assertThat(Either.<String, String>right(null).map(s -> "left", s -> "right"), is("right"));
    }
}