/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.fieldcaps;

import org.elasticsearch.action.fieldcaps.FieldCapabilities;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.plugins.MapperPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Before;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

public class FieldCapabilitiesIT extends ESIntegTestCase {

    @Before
    public void setUp() throws Exception {
        super.setUp();

        XContentBuilder oldIndexMapping = XContentFactory.jsonBuilder()
            .startObject()
                .startObject("_doc")
                    .startObject("properties")
                        .startObject("distance")
                            .field("type", "double")
                        .endObject()
                        .startObject("route_length_miles")
                            .field("type", "alias")
                            .field("path", "distance")
                        .endObject()
                        .startObject("playlist")
                            .field("type", "text")
                        .endObject()
                        .startObject("secret_soundtrack")
                            .field("type", "alias")
                            .field("path", "playlist")
                        .endObject()
                    .endObject()
                .endObject()
            .endObject();
        assertAcked(prepareCreate("old_index").addMapping("_doc", oldIndexMapping));

        XContentBuilder newIndexMapping = XContentFactory.jsonBuilder()
            .startObject()
                .startObject("_doc")
                    .startObject("properties")
                        .startObject("distance")
                            .field("type", "text")
                        .endObject()
                        .startObject("route_length_miles")
                            .field("type", "double")
                        .endObject()
                    .endObject()
                .endObject()
            .endObject();
        assertAcked(prepareCreate("new_index").addMapping("_doc", newIndexMapping));
    }

    public static class FieldFilterPlugin extends Plugin implements MapperPlugin {
        @Override
        public Function<String, Predicate<String>> getFieldFilter() {
            return index -> field -> !field.equals("playlist");
        }
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singleton(FieldFilterPlugin.class);
    }

    public void testFieldAlias() {
        FieldCapabilitiesResponse response = client().prepareFieldCaps().setFields("distance", "route_length_miles")
            .get();

        // Ensure the response has entries for both requested fields.
        assertTrue(response.get().containsKey("distance"));
        assertTrue(response.get().containsKey("route_length_miles"));

        // Check the capabilities for the 'distance' field.
        Map<String, FieldCapabilities> distance = response.getField("distance");
        assertEquals(2, distance.size());

        assertTrue(distance.containsKey("double"));
        assertEquals(
            new FieldCapabilities("distance", "double", true, true, new String[] {"old_index"}, null, null),
            distance.get("double"));

        assertTrue(distance.containsKey("text"));
        assertEquals(
            new FieldCapabilities("distance", "text", true, false, new String[] {"new_index"}, null, null),
            distance.get("text"));

        // Check the capabilities for the 'route_length_miles' alias.
        Map<String, FieldCapabilities> routeLength = response.getField("route_length_miles");
        assertEquals(1, routeLength.size());

        assertTrue(routeLength.containsKey("double"));
        assertEquals(
            new FieldCapabilities("route_length_miles", "double", true, true),
            routeLength.get("double"));
    }

    public void testFieldAliasWithWildcard() {
        FieldCapabilitiesResponse response = client().prepareFieldCaps().setFields("route*")
            .get();

        assertEquals(1, response.get().size());
        assertTrue(response.get().containsKey("route_length_miles"));
    }

    public void testFieldAliasFiltering() {
        FieldCapabilitiesResponse response = client().prepareFieldCaps().setFields(
            "secret-soundtrack", "route_length_miles")
            .get();
        assertEquals(1, response.get().size());
        assertTrue(response.get().containsKey("route_length_miles"));
    }

    public void testFieldAliasFilteringWithWildcard() {
        FieldCapabilitiesResponse response = client().prepareFieldCaps()
            .setFields("distance", "secret*")
            .get();
        assertEquals(1, response.get().size());
        assertTrue(response.get().containsKey("distance"));
    }
}
