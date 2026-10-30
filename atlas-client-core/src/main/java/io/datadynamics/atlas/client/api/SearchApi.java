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
 * {@code /api/atlas/v2/search}: basic, DSL and full-text search.
 * <p>
 * The {@code *Stream} variants page through all results lazily, so callers never deal with
 * {@code limit}/{@code offset} themselves.
 */
public final class SearchApi {

    private static final String BASE = "/api/atlas/v2/search";
    public static final int DEFAULT_PAGE_SIZE = 100;

    private final AtlasTransport transport;

    public SearchApi(AtlasTransport transport) {
        this.transport = transport;
    }

    /** One page of basic search results, as specified by {@code limit}/{@code offset} in the parameters. */
    public AtlasSearchResult basic(SearchParameters parameters) {
        return transport.call(POST, BASE + "/basic", null, parameters, AtlasSearchResult.class);
    }

    /** All basic search results, fetched lazily page by page. */
    public Stream<AtlasEntityHeader> basicStream(SearchParameters parameters) {
        return basicStream(parameters, DEFAULT_PAGE_SIZE);
    }

    public Stream<AtlasEntityHeader> basicStream(SearchParameters parameters, int pageSize) {
        return Pages.stream(pageSize, offset -> {
            SearchParameters page = parameters.copy();
            page.setLimit(pageSize);
            page.setOffset(offset);
            return entitiesOf(basic(page));
        });
    }

    /**
     * One page of DSL search results.
     *
     * @param query          DSL query, e.g. {@code hive_table where name = "sales"}
     * @param limit          maximum results, or {@code null} for the server default
     * @param offset         results to skip, or {@code null}
     */
    public AtlasSearchResult dsl(String query, Integer limit, Integer offset) {
        QueryParams params = QueryParams.create().add("query", query).add("limit", limit).add("offset", offset);
        return transport.call(GET, BASE + "/dsl", params, null, AtlasSearchResult.class);
    }

    /**
     * All entities matching a DSL query, fetched lazily page by page.
     * The query must not contain its own {@code limit}/{@code offset} clause.
     */
    public Stream<AtlasEntityHeader> dslStream(String query) {
        return dslStream(query, DEFAULT_PAGE_SIZE);
    }

    public Stream<AtlasEntityHeader> dslStream(String query, int pageSize) {
        return Pages.stream(pageSize, offset -> entitiesOf(dsl(query, pageSize, offset)));
    }

    public AtlasSearchResult fullText(String query, Integer limit, Integer offset, boolean excludeDeletedEntities) {
        QueryParams params = QueryParams.create()
                .add("query", query)
                .add("limit", limit)
                .add("offset", offset)
                .add("excludeDeletedEntities", excludeDeletedEntities);
        return transport.call(GET, BASE + "/fulltext", params, null, AtlasSearchResult.class);
    }

    private static List<AtlasEntityHeader> entitiesOf(AtlasSearchResult result) {
        return result == null ? List.of() : result.getEntities();
    }
}
