package uk.org.thehickses.permute;

import java.util.stream.IntStream;

@FunctionalInterface
public interface IntPartialResultValidator
{
    void validate(IntStream result) throws ValidationException;
}
