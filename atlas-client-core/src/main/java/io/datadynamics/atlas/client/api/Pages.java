package io.datadynamics.atlas.client.api;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Turns offset-based Atlas paging into a lazy {@link Stream}.
 * <p>
 * Paging stops at the first empty page rather than the first short page: Atlas basic search
 * can return fewer results than {@code limit} before the end when filters are applied after
 * the index query, so a short page does not reliably mean "last page".
 */
final class Pages {

    private Pages() {
    }

    /**
     * @param pageSize    number of results requested per page
     * @param pageFetcher fetches the page starting at the given offset
     */
    static <T> Stream<T> stream(int pageSize, IntFunction<List<T>> pageFetcher) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }
        Iterator<T> iterator = new Iterator<>() {
            private int nextOffset;
            private Iterator<T> current = Collections.emptyIterator();
            private boolean exhausted;

            @Override
            public boolean hasNext() {
                while (!current.hasNext() && !exhausted) {
                    List<T> page = pageFetcher.apply(nextOffset);
                    if (page == null || page.isEmpty()) {
                        exhausted = true;
                    } else {
                        nextOffset += pageSize;
                        current = page.iterator();
                    }
                }
                return current.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return current.next();
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }
}
