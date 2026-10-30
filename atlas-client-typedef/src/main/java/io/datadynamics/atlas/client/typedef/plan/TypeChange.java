package io.datadynamics.atlas.client.typedef.plan;

import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.TypeCategory;

import java.util.List;

/**
 * Planned change of one type.
 *
 * @param details human readable list of what changes ({@code + attribute 'owner' (string)}, ...)
 * @param payload the definition that will be sent to Atlas: the desired definition for a create,
 *                the server definition merged with the desired one for an update, the server
 *                definition for a delete
 */
public record TypeChange(String name, TypeCategory category, Action action, List<String> details,
                         AtlasBaseTypeDef payload) {

    public enum Action {
        CREATE("+", "create"),
        UPDATE("~", "update"),
        UNCHANGED("=", "unchanged"),
        DELETE("-", "delete");

        private final String symbol;
        private final String label;

        Action(String symbol, String label) {
            this.symbol = symbol;
            this.label = label;
        }

        public String symbol() {
            return symbol;
        }

        public String label() {
            return label;
        }
    }

    public TypeChange {
        details = List.copyOf(details);
    }
}
