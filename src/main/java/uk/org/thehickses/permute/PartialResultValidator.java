package uk.org.thehickses.permute;

import java.util.stream.Stream;

@FunctionalInterface
public interface PartialResultValidator<T>
{
    void validate(Stream<T> result) throws ValidationException;
}
