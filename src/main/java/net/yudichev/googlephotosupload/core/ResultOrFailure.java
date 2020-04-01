package net.yudichev.googlephotosupload.core;

import java.util.Optional;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ResultOrFailure<T> {
    private static final Object NO_VALUE = new Object();
    private final Either<T, String> eitherResultOrFailure;

    private ResultOrFailure(Either<T, String> eitherResultOrFailure) {
        this.eitherResultOrFailure = checkNotNull(eitherResultOrFailure);
    }

    public static <T> ResultOrFailure<T> success(T value) {
        return new ResultOrFailure<>(Either.left(value));
    }

    public static ResultOrFailure<Object> success() {
        return new ResultOrFailure<>(Either.left(NO_VALUE));
    }

    public static <T> ResultOrFailure<T> failure(String error) {
        return new ResultOrFailure<>(Either.right(error));
    }

    public <U> U map(
            Function<? super T, ? extends U> resultFunc,
            Function<? super String, ? extends U> errorFunc) {
        return eitherResultOrFailure.map(resultFunc, errorFunc);
    }

    public Optional<String> toFailure() {
        return eitherResultOrFailure.getRight();
    }
}
