package net.yudichev.googlephotosupload.core;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Optional;

/**
 * Matches an empty Optional.
 */
class EmptyOptional<T> extends TypeSafeDiagnosingMatcher<Optional<? extends T>> {

    @Override
    protected boolean matchesSafely(Optional<? extends T> item, Description mismatchDescription) {
        if (item.isPresent()) {
            mismatchDescription
                    .appendText("was present with ")
                    .appendValue(item.get());
            return false;
        }
        return true;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("an Optional that's empty");
    }
}
