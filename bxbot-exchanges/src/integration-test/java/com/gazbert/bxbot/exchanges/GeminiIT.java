/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Gareth Jon Lynch
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.gazbert.bxbot.exchanges;

import com.gazbert.bxbot.exchange.api.*;
import com.gazbert.bxbot.trading.api.BalanceInfo;
import com.gazbert.bxbot.trading.api.MarketOrderBook;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Basic integration testing with Gemini exchange.
 *
 * @author gazbert
 */
public class GeminiIT {

    // Canned test data
    private static final String MARKET_ID = "ethbtc";
    private static final BigDecimal SELL_ORDER_PRICE = new BigDecimal("10000.12345");
    private static final BigDecimal SELL_ORDER_QUANTITY = new BigDecimal("0.001");

    // Exchange Adapter config for the tests
    private static final String KEY = "key123";
    private static final String SECRET = "notGonnaTellYa";
    private static final List<Integer> nonFatalNetworkErrorCodes = Arrays.asList(502, 503, 504);
    private static final List<String> nonFatalNetworkErrorMessages = Arrays.asList(
            "Connection refused", "Connection reset", "Remote host closed connection during handshake");

    private ExchangeConfig exchangeConfig;
    private AuthenticationConfig authenticationConfig;
    private NetworkConfig networkConfig;
    private OptionalConfig optionalConfig;

    /*
     * Create some exchange config - the TradingEngine would normally do this.
     */
    @Before
    public void setupForEachTest() throws Exception {

        authenticationConfig = createMock(AuthenticationConfig.class);
        expect(authenticationConfig.getItem("key")).andReturn(KEY);
        expect(authenticationConfig.getItem("secret")).andReturn(SECRET);

        networkConfig = createMock(NetworkConfig.class);
        expect(networkConfig.getConnectionTimeout()).andReturn(30);
        expect(networkConfig.getNonFatalErrorCodes()).andReturn(nonFatalNetworkErrorCodes);
        expect(networkConfig.getNonFatalErrorMessages()).andReturn(nonFatalNetworkErrorMessages);

        optionalConfig = createMock(OptionalConfig.class);
        expect(optionalConfig.getItem("buy-fee")).andReturn("0.25");
        expect(optionalConfig.getItem("sell-fee")).andReturn("0.25");

        exchangeConfig = createMock(ExchangeConfig.class);
        expect(exchangeConfig.getAuthenticationConfig()).andReturn(authenticationConfig);
        expect(exchangeConfig.getNetworkConfig()).andReturn(networkConfig);
        expect(exchangeConfig.getOptionalConfig()).andReturn(optionalConfig);
    }

    @Test
    public void testPublicApiCalls() throws Exception {

        replay(authenticationConfig, networkConfig, optionalConfig, exchangeConfig);

        final ExchangeAdapter exchangeAdapter = new GeminiExchangeAdapter();
        exchangeAdapter.init(exchangeConfig);

        assertNotNull(exchangeAdapter.getLatestMarketPrice(MARKET_ID));

        final MarketOrderBook orderBook = exchangeAdapter.getMarketOrders(MARKET_ID);
        assertFalse(orderBook.getBuyOrders().isEmpty());
        assertFalse(orderBook.getSellOrders().isEmpty());

        verify(authenticationConfig, networkConfig, optionalConfig, exchangeConfig);
    }

    /*
     * You'll need to change the KEY, SECRET, constants to real-world values.
     */
    @Ignore("Disabled. Integration testing authenticated API calls requires your secret credentials!")
    @Test
    public void testAuthenticatedApiCalls() throws Exception {

        replay(authenticationConfig, networkConfig, optionalConfig, exchangeConfig);

        final ExchangeAdapter exchangeAdapter = new GeminiExchangeAdapter();
        exchangeAdapter.init(exchangeConfig);

        final BalanceInfo balanceInfo = exchangeAdapter.getBalanceInfo();
        assertNotNull(balanceInfo.getBalancesAvailable().get("BTC"));

//      // Careful here - make sure the SELL_ORDER_PRICE is sensible!
//        final String orderId = exchangeAdapter.createOrder(MARKET_ID, OrderType.SELL, SELL_ORDER_QUANTITY, SELL_ORDER_PRICE);
//        final List<OpenOrder> openOrders = exchangeAdapter.getYourOpenOrders(MARKET_ID);
//        assertTrue(openOrders.stream().anyMatch(o -> o.getId().equals(orderId)));
//        assertTrue(exchangeAdapter.cancelOrder(orderId, MARKET_ID));

        verify(authenticationConfig, networkConfig, optionalConfig, exchangeConfig);
    }
}
