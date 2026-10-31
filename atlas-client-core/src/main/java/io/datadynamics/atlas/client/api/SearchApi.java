package io.datadynamics.atlas.client.api;

import io.datadynamics.atlas.client.http.AtlasTransport;
import io.datadynamics.atlas.client.http.QueryParams;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.search.AtlasSearchResult;
import io.datadynamics.atlas.client.model.search.SearchParameters;

import java.util.List;
import java.util.stream.Stream;

import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.POST;

/**
 * {@code /api/atlas/v2/search} 엔드포인트로 basic 검색, DSL 검색, 전문(full-text) 검색을 합니다.
 * <p>
 * {@code *Stream} 메서드들은 모든 결과를 필요할 때 한 페이지씩 가져오므로, 호출하는 쪽이 {@code limit}/{@code offset}을
 * 직접 다룰 필요가 없습니다. 스트림을 끝까지 소비하는 동안 페이지마다 요청이 한 번씩 나갑니다.
 * <p>
 * 상태를 가지지 않으므로 여러 스레드에서 함께 써도 됩니다.
 */
public final class SearchApi {

    private static final String BASE = "/api/atlas/v2/search";
    /** {@code *Stream} 메서드에서 페이지 크기를 지정하지 않았을 때 쓰는 기본값입니다. */
    public static final int DEFAULT_PAGE_SIZE = 100;

    private final AtlasTransport transport;

    /**
     * API 객체를 만듭니다.
     *
     * @param transport 요청을 보낼 전송 계층
     */
    public SearchApi(AtlasTransport transport) {
        this.transport = transport;
    }

    /**
     * basic 검색 결과 한 페이지를 가져옵니다. 페이지는 파라미터의 {@code limit}/{@code offset}으로 정합니다.
     * {@code POST /api/atlas/v2/search/basic}을 호출합니다.
     *
     * @param parameters 검색 조건
     * @return 검색 결과 한 페이지
     */
    public AtlasSearchResult basic(SearchParameters parameters) {
        return transport.call(POST, BASE + "/basic", null, parameters, AtlasSearchResult.class);
    }

    /**
     * basic 검색의 모든 결과를 기본 페이지 크기({@value #DEFAULT_PAGE_SIZE})로 한 페이지씩 지연해서 가져옵니다.
     *
     * @param parameters 검색 조건. {@code limit}/{@code offset}은 무시되고 페이지마다 새로 정해집니다
     * @return 검색된 엔티티 요약의 지연 스트림
     */
    public Stream<AtlasEntityHeader> basicStream(SearchParameters parameters) {
        return basicStream(parameters, DEFAULT_PAGE_SIZE);
    }

    /**
     * basic 검색의 모든 결과를 지정한 페이지 크기로 한 페이지씩 지연해서 가져옵니다.
     * <p>
     * 페이지마다 파라미터를 복사해 {@code limit}과 {@code offset}만 바꿔 보내므로, 넘긴 파라미터 객체는 바뀌지 않습니다.
     *
     * @param parameters 검색 조건
     * @param pageSize   한 번에 요청할 결과 수(1 이상)
     * @return 검색된 엔티티 요약의 지연 스트림
     * @throws IllegalArgumentException {@code pageSize}가 0 이하일 때
     */
    public Stream<AtlasEntityHeader> basicStream(SearchParameters parameters, int pageSize) {
        return Pages.stream(pageSize, offset -> {
            SearchParameters page = parameters.copy();
            page.setLimit(pageSize);
            page.setOffset(offset);
            return entitiesOf(basic(page));
        });
    }

    /**
     * DSL 검색 결과 한 페이지를 가져옵니다. {@code GET /api/atlas/v2/search/dsl}을 호출합니다.
     *
     * @param query  DSL 쿼리(예: {@code hive_table where name = "sales"})
     * @param limit  최대 결과 수, 서버 기본값을 쓰려면 {@code null}
     * @param offset 건너뛸 결과 수, 없으면 {@code null}
     * @return 검색 결과 한 페이지. {@code select} 쿼리면 {@link AtlasSearchResult#getAttributes()}에 표 형태로 담깁니다
     */
    public AtlasSearchResult dsl(String query, Integer limit, Integer offset) {
        QueryParams params = QueryParams.create().add("query", query).add("limit", limit).add("offset", offset);
        return transport.call(GET, BASE + "/dsl", params, null, AtlasSearchResult.class);
    }

    /**
     * DSL 쿼리에 맞는 모든 엔티티를 기본 페이지 크기로 한 페이지씩 지연해서 가져옵니다.
     * 쿼리 안에 {@code limit}/{@code offset} 절을 직접 넣으면 안 됩니다.
     *
     * @param query DSL 쿼리
     * @return 검색된 엔티티 요약의 지연 스트림
     */
    public Stream<AtlasEntityHeader> dslStream(String query) {
        return dslStream(query, DEFAULT_PAGE_SIZE);
    }

    /**
     * DSL 쿼리에 맞는 모든 엔티티를 지정한 페이지 크기로 한 페이지씩 지연해서 가져옵니다.
     * 쿼리 안에 {@code limit}/{@code offset} 절을 직접 넣으면 안 됩니다.
     *
     * @param query    DSL 쿼리
     * @param pageSize 한 번에 요청할 결과 수(1 이상)
     * @return 검색된 엔티티 요약의 지연 스트림
     * @throws IllegalArgumentException {@code pageSize}가 0 이하일 때
     */
    public Stream<AtlasEntityHeader> dslStream(String query, int pageSize) {
        return Pages.stream(pageSize, offset -> entitiesOf(dsl(query, pageSize, offset)));
    }

    /**
     * 전문(full-text) 검색 결과 한 페이지를 가져옵니다. {@code GET /api/atlas/v2/search/fulltext}를 호출합니다.
     *
     * @param query                  검색어
     * @param limit                  최대 결과 수, 서버 기본값을 쓰려면 {@code null}
     * @param offset                 건너뛸 결과 수, 없으면 {@code null}
     * @param excludeDeletedEntities 삭제된 엔티티를 결과에서 뺄지 여부
     * @return 검색 결과 한 페이지
     */
    public AtlasSearchResult fullText(String query, Integer limit, Integer offset, boolean excludeDeletedEntities) {
        QueryParams params = QueryParams.create()
                .add("query", query)
                .add("limit", limit)
                .add("offset", offset)
                .add("excludeDeletedEntities", excludeDeletedEntities);
        return transport.call(GET, BASE + "/fulltext", params, null, AtlasSearchResult.class);
    }

    // 응답이 비어 있어 결과 객체가 null이면 빈 목록을 돌려줍니다. 빈 목록은 Pages에게 "마지막 페이지"라는 뜻입니다.
    private static List<AtlasEntityHeader> entitiesOf(AtlasSearchResult result) {
        return result == null ? List.of() : result.getEntities();
    }
}
