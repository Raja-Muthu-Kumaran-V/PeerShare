package peershare.util;

import java.util.Objects;

/**
 * A simple generic pair of two values of independent types. Used around the
 * GUI wherever two related values need to travel together without writing a
 * one-off class for each case - e.g. pairing a table row's underlying object
 * with its display label.
 */
public final class Pair<A, B> {

    private final A first;
    private final B second;

    private Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public static <A, B> Pair<A, B> of(A first, B second) {
        return new Pair<>(first, second);
    }

    public A first() { return first; }
    public B second() { return second; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pair<?, ?> other)) return false;
        return Objects.equals(first, other.first) && Objects.equals(second, other.second);
    }

    @Override
    public int hashCode() { return Objects.hash(first, second); }

    @Override
    public String toString() { return "(" + first + ", " + second + ")"; }
}
