package uk.org.thehickses.permute;

import static org.assertj.core.api.Assertions.*;

import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class PermuterTest
{
    @Test
    void test5()
    {
        assertThat(new Permuter<>("a", "b", "c", "d", "e").permute().count())
                .isEqualTo(LongStream.rangeClosed(1, 5).reduce((a, b) -> a * b).getAsLong());
    }

    @Test
    void test10()
    {
        assertThat(
                new Permuter<>("a", "b", "c", "d", "e", "f", "g", "h", "i", "j").permute().count())
                        .isEqualTo(
                                LongStream.rangeClosed(1, 10).reduce((a, b) -> a * b).getAsLong());
    }
}
