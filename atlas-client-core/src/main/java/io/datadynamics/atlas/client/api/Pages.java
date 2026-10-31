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
 * offset 기반의 Atlas 페이지 조회를 지연(lazy) {@link Stream}으로 바꿉니다.
 * <p>
 * 페이지 조회는 결과가 {@code limit}보다 적은 첫 페이지가 아니라 처음으로 빈 페이지를 만났을 때 멈춥니다.
 * Atlas basic 검색은 인덱스 조회 뒤에 필터를 적용하는 경우, 끝에 도달하기 전에도 {@code limit}보다 적은
 * 결과를 돌려줄 수 있습니다. 그래서 결과가 적은 페이지가 곧 "마지막 페이지"라는 보장이 없습니다.
 */
final class Pages {

    // 정적 메서드만 있으므로 인스턴스를 만들지 못하게 막습니다.
    private Pages() {
    }

    /**
     * 페이지를 차례로 가져오는 지연 스트림을 만듭니다. 첫 페이지도 스트림 요소를 처음 요청할 때 가져옵니다.
     * <p>
     * offset은 0에서 시작해 페이지를 받을 때마다 받은 개수가 아니라 {@code pageSize}만큼 늘어납니다.
     * 스트림은 순차(sequential) 스트림이며 스레드 안전하지 않습니다.
     *
     * @param pageSize    페이지마다 요청하는 결과 수
     * @param pageFetcher 주어진 offset에서 시작하는 페이지를 가져오는 함수. {@code null}이나 빈 목록은 끝을 뜻합니다
     * @param <T>         요소 타입
     * @return 모든 페이지의 요소를 이어 붙인 지연 스트림
     * @throws IllegalArgumentException {@code pageSize}가 0 이하일 때
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
                // 현재 페이지를 다 썼으면 다음 페이지를 가져옵니다. 빈 페이지를 만나면 더는 요청하지 않습니다.
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

            // hasNext()가 필요하면 다음 페이지를 가져오므로, 요소를 꺼내기 전에 반드시 먼저 호출합니다.
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
