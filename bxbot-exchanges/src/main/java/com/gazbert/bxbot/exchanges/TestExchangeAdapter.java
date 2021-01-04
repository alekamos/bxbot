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

import com.gazbert.bxbot.exchanges.trading.api.impl.BalanceInfoImpl;
import com.gazbert.bxbot.exchanges.trading.api.impl.OpenOrderImpl;
import com.gazbert.bxbot.trading.api.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Dummy Exchange adapter used to keep the bot up and running for engine and strategy testing.
 *
 * <p>Makes public calls to the Bitstamp exchange. It does not trade. All private (authenticated)
 * requests are stubbed.
 *
 * <p>Might be handy for 'dry testing' your algos.
 *
 * @author gazbert
 * @since 1.0
 */
public final class TestExchangeAdapter extends KrakenExchangeAdapter {

  private static final String DUMMY_BALANCE = "100.00";

  @Override
  public List<OpenOrder> getYourOpenOrders(String marketId) throws TradingApiException, ExchangeNetworkException {


    BigDecimal bid = getTicker(marketId).getBid();
    //tolgo tipo il 0.2% da quest'ordine
    BigDecimal prezzoBid = bid.add(new BigDecimal(0.007).multiply(bid));

    List<OpenOrder> orders = new ArrayList<>();
    /*
    final OpenOrder order =
            new OpenOrderImpl(
                    Long.toString(123456),
                    new Date(),
                    marketId,
                    OrderType.BUY,
                    prezzoBid,
                    new BigDecimal(0.0075),
                    null, // orig_quantity - not provided by stamp :-(
                    null
            );
    orders.add(order);
*/

    return orders;
  }

  @Override
  public String createOrder(
      String marketId, OrderType orderType, BigDecimal quantity, BigDecimal price) {
    return "DUMMY_ORDER_ID: " + UUID.randomUUID().toString();
  }

  /* marketId is not needed for cancelling orders on this exchange.*/
  @Override
  public boolean cancelOrder(String orderId, String marketIdNotNeeded) {
    return true;
  }

  @Override
  public BalanceInfo getBalanceInfo() {
    final Map<String, BigDecimal> balancesAvailable = new HashMap<>();
    balancesAvailable.put("BTC", new BigDecimal(DUMMY_BALANCE));
    balancesAvailable.put("USD", new BigDecimal(DUMMY_BALANCE));
    balancesAvailable.put("EUR", new BigDecimal(DUMMY_BALANCE));
    balancesAvailable.put("LTC", new BigDecimal(DUMMY_BALANCE));
    balancesAvailable.put("XRP", new BigDecimal(DUMMY_BALANCE));
    balancesAvailable.put("XXBT", new BigDecimal(DUMMY_BALANCE));
    balancesAvailable.put("ZEUR", new BigDecimal(DUMMY_BALANCE));

    final Map<String, BigDecimal> balancesOnOrder = new HashMap<>();
    balancesOnOrder.put("BTC", new BigDecimal(DUMMY_BALANCE));
    balancesOnOrder.put("USD", new BigDecimal(DUMMY_BALANCE));
    balancesOnOrder.put("EUR", new BigDecimal(DUMMY_BALANCE));
    balancesOnOrder.put("LTC", new BigDecimal(DUMMY_BALANCE));
    balancesOnOrder.put("XRP", new BigDecimal(DUMMY_BALANCE));
    balancesOnOrder.put("XXBT", new BigDecimal(DUMMY_BALANCE));
    balancesOnOrder.put("ZEUR", new BigDecimal(DUMMY_BALANCE));

    return new BalanceInfoImpl(balancesAvailable, balancesOnOrder);
  }

  @Override
  public String getImplName() {
    return "Dummy Test Adapter - based on Bitstamp HTTP API v2";
  }
}
