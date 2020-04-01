package net.yudichev.googlephotosupload.core;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Function;

public final class Either<L, R> {
    @Nullable
    private final
    L left;
    @Nullable
    private final
    R right;
    private final boolean isLeft;

    private Either(@Nullable L left, @Nullable R right, boolean isLeft) {
        this.left = left;
        this.right = right;
        this.isLeft = isLeft;
    }

    public static <L, R> Either<L, R> left(L value) {
        return new Either<>(value, null, true);
    }

    public static <L, R> Either<L, R> right(R value) {
        return new Either<>(null, value, false);
    }

    public <T> T map(
            Function<? super L, ? extends T> lFunc,
            Function<? super R, ? extends T> rFunc) {
        return isLeft ? lFunc.apply(left) : rFunc.apply(right);
    }

    public Optional<L> getLeft() {
        return Optional.ofNullable(left);
    }

    public Optional<R> getRight() {
        return Optional.ofNullable(right);
    }
}