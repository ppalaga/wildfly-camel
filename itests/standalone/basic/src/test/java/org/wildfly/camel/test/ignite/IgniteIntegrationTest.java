/*
 * #%L
 * Wildfly Camel :: Testsuite
 * %%
 * Copyright (C) 2013 - 2016 RedHat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.wildfly.camel.test.ignite;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.cache.Cache.Entry;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.component.ignite.cache.IgniteCacheComponent;
import org.apache.camel.component.ignite.events.IgniteEventsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.query.Query;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.Event;
import org.apache.ignite.events.EventType;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.extension.camel.CamelAware;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
@CamelAware
@RunWith(Arquillian.class)
public class IgniteIntegrationTest {

    private static final Random rnd = new Random();

    @Deployment
    public static JavaArchive createdeployment() {
        return ShrinkWrap.create(JavaArchive.class, "camel-ignite-tests");
    }

    @Test
    public void testCache() throws Exception {

        final String cacheName = randomCacheName();

        final CamelContext camelctx = new DefaultCamelContext();

        final IgniteCacheComponent igniteComponent = camelctx.getComponent("ignite-cache", IgniteCacheComponent.class);
        igniteComponent.setIgniteConfiguration(new IgniteConfiguration());

        camelctx.start();

        try {

            final IgniteCache<String, String> cache = igniteComponent.getIgnite().getOrCreateCache(cacheName);
            Assert.assertEquals(0, cache.size(CachePeekMode.ALL));

            final ProducerTemplate template = camelctx.createProducerTemplate();

            /* Put single */
            template.requestBodyAndHeader("ignite-cache:" + cacheName + "?operation=PUT", "v1",
                    IgniteConstants.IGNITE_CACHE_KEY, "k1");
            Assert.assertEquals(1, cache.size(CachePeekMode.ALL));
            Assert.assertEquals("v1", cache.get("k1"));

            cache.removeAll();
            Assert.assertEquals(0, cache.size(CachePeekMode.ALL));

            /* Put multiple */
            final Map<String, String> entries = new LinkedHashMap<String, String>() {
                private static final long serialVersionUID = 1L;
            {
                put("k2", "v2");
                put("k3", "v3");
            }};
            template.requestBody("ignite-cache:" + cacheName + "?operation=PUT", entries);
            Assert.assertEquals(2, cache.size(CachePeekMode.ALL));
            Assert.assertEquals("v2", cache.get("k2"));
            Assert.assertEquals("v3", cache.get("k3"));

            /* Get one */
            Assert.assertEquals("v2", template.requestBody("ignite-cache:" + cacheName + "?operation=GET", "k2", String.class));
            Assert.assertEquals("v3", template.requestBodyAndHeader("ignite-cache:" + cacheName + "?operation=GET", "this value won't be used", IgniteConstants.IGNITE_CACHE_KEY, "k3", String.class));

            Set<String> keys = new LinkedHashSet<>(Arrays.asList("k2", "k3"));
            /* Get many */
            Assert.assertEquals(entries, template.requestBody("ignite-cache:" + cacheName + "?operation=GET", keys , Map.class));

            /* Size */
            Assert.assertEquals(2, template.requestBody("ignite-cache:" + cacheName + "?operation=SIZE", keys , Integer.class).intValue());

            /* Query */
            Query<Entry<String, String>> query = new ScanQuery<String, String>(new IgniteBiPredicate<String, String>() {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean apply(String key, String value) {
                    return Integer.parseInt(key.replace("k", "")) >= 3;
                }
            });
            List<?> results = template.requestBodyAndHeader("ignite-cache:" + cacheName + "?operation=QUERY", keys, IgniteConstants.IGNITE_CACHE_QUERY, query, List.class);
            Assert.assertEquals(1, results.size());

            /* Remove */
            template.requestBody("ignite-cache:" + cacheName + "?operation=REMOVE", "k2");
            Assert.assertEquals(1, cache.size(CachePeekMode.ALL));
            Assert.assertNull(cache.get("k2"));

            template.requestBodyAndHeader("ignite-cache:" + cacheName + "?operation=REMOVE", "this value won't be used", IgniteConstants.IGNITE_CACHE_KEY, "k3");
            Assert.assertEquals(0, cache.size(CachePeekMode.ALL));
            Assert.assertNull(cache.get("k3"));


            /* Clear */
            cache.put("k4", "v4");
            cache.put("k5", "v5");
            Assert.assertEquals(2, cache.size(CachePeekMode.ALL));
            template.requestBody("ignite-cache:" + cacheName + "?operation=CLEAR", "this value won't be used");
            Assert.assertEquals(0, cache.size(CachePeekMode.ALL));

            cache.clear();

        } finally {
            camelctx.stop();
        }

    }

    private static String randomCacheName() {
        return IgniteIntegrationTest.class.getSimpleName() + "-cache-" + Math.abs(rnd.nextInt());
    }

    @Test
    public void testEvents() throws Exception {
        final String cacheName = randomCacheName();

        final CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("ignite-events:" + cacheName).to("mock:test1");
            }
        });

        final IgniteEventsComponent igniteComponent = camelctx.getComponent("ignite-events",
                IgniteEventsComponent.class);
        igniteComponent.setIgniteConfiguration(
                new IgniteConfiguration().setIncludeEventTypes(EventType.EVTS_ALL_MINUS_METRIC_UPDATE));

        final MockEndpoint mockEndpoint = camelctx.getEndpoint("mock:test1", MockEndpoint.class);

        camelctx.start();
        try {

            final IgniteCache<String, String> cache = igniteComponent.getIgnite().getOrCreateCache(cacheName);

            /* Generate some cache activity */
            cache.put("abc", "123");
            cache.get("abc");
            cache.remove("abc");
            cache.withExpiryPolicy(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MILLISECONDS, 100)).create())
                    .put("abc", "123");

            Thread.sleep(150); // wait for the expiration of the above entry

            cache.get("abc");

            final List<Integer> actualTypes = mockEndpoint.getExchanges().stream() //
                    /*
                     * There are some DiscoveryEvents here and there in the list that are hard to predict so let's keep
                     * just the CacheEvents we have generated above
                     */
                    .filter(exchange -> exchange.getIn().getBody(Event.class) instanceof CacheEvent)
                    .map(exchange -> exchange.getIn().getBody(Event.class).type()).collect(Collectors.toList());

            final List<Integer> expectedTypes = Arrays.asList(EventType.EVT_CACHE_STARTED,
                    EventType.EVT_CACHE_ENTRY_CREATED, EventType.EVT_CACHE_OBJECT_PUT, EventType.EVT_CACHE_OBJECT_READ,
                    EventType.EVT_CACHE_OBJECT_REMOVED, EventType.EVT_CACHE_OBJECT_PUT,
                    EventType.EVT_CACHE_OBJECT_EXPIRED);

            Assert.assertEquals(expectedTypes, actualTypes);

        } finally {
            camelctx.stop();
        }

    }

}
