package uk.org.thehickses.permute;

@FunctionalInterface
public interface PartialResultValidator<T>
{
    void validate(T result) throws ValidationException;
}
