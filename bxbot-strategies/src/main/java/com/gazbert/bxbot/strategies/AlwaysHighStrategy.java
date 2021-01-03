/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Gareth Jon Lynch
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

package com.gazbert.bxbot.strategies;

import com.gazbert.bxbot.strategy.api.StrategyConfig;
import com.gazbert.bxbot.strategy.api.StrategyException;
import com.gazbert.bxbot.strategy.api.TradingStrategy;
import com.gazbert.bxbot.trading.api.*;
import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;


@Component("alwaysHighStrategy") // used to load the strategy using Spring bean injection
public class AlwaysHighStrategy implements TradingStrategy {

  private static final Logger LOG = LogManager.getLogger();
  /** The decimal format for the logs. */
  private static final String DECIMAL_FORMAT = "#.###";

  /** Reference to the main Trading API. */
  private TradingApi tradingApi;

  /** The market this strategy is trading on. */
  private Market market;

  /** The state of the order. */
  private OrderState lastOrder;


  private BigDecimal counterCurrencyBuyOrderAmount;
  private BigDecimal minimoGuadagnoPerc;
  private BigDecimal perditaMassimaPerc;
  private BigDecimal perditaSottoMassimoPerc;
  private BigDecimal highiestPriceReached = new BigDecimal(0);
  private BigDecimal cuscinettoBuySell = new BigDecimal(0.025);


  /**
   * Initialises the Trading Strategy. Called once by the Trading Engine when the bot starts up;
   * it's a bit like a servlet init() method.
   *
   * @param tradingApi the Trading API. Use this to make trades and stuff.
   * @param market the market for this strategy. This is the market the strategy is currently
   *     running on - you wire this up in the markets.yaml and strategies.yaml files.
   * @param config configuration for the strategy. Contains any (optional) config you set up in the
   *     strategies.yaml file.
   */
  @Override
  public void init(TradingApi tradingApi, Market market, StrategyConfig config) {
    LOG.info(() -> "Initialising Trading Strategy...");
    this.tradingApi = tradingApi;
    this.market = market;
    getConfigForStrategy(config);
    LOG.info(() -> "Trading Strategy initialised successfully!");
  }

  /**
   * This is the main execution method of the Trading Strategy. It is where your algorithm lives.
   *
   * <p>It is called by the Trading Engine during each trade cycle, e.g. every 60s. The trade cycle
   * is configured in the {project-root}/config/engine.yaml file.
   *
   * @throws StrategyException if something unexpected occurs. This tells the Trading Engine to
   *     shutdown the bot immediately to help prevent unexpected losses.
   */
  @Override
  public void execute() throws StrategyException {
    LOG.info(() -> market.getName() + " Checking order status...");

    try {
      // Grab the latest order book for the market.
      final MarketOrderBook orderBook = tradingApi.getMarketOrders(market.getId());

      final List<MarketOrder> buyOrders = orderBook.getBuyOrders();
      if (buyOrders.isEmpty()) {
        LOG.warn(() ->"Exchange returned empty Buy Orders. Ignoring this trade window. OrderBook: "+ orderBook);
        return;
      }

      final List<MarketOrder> sellOrders = orderBook.getSellOrders();
      if (sellOrders.isEmpty()) {
        LOG.warn(() ->"Exchange returned empty Sell Orders. Ignoring this trade window. OrderBook: "+ orderBook);
        return;
      }

      // Get the current BID and ASK spot prices.
      final BigDecimal currentBidPrice = buyOrders.get(0).getPrice();
      final BigDecimal currentAskPrice = sellOrders.get(0).getPrice();

      LOG.info(() ->market.getName()+" Current BID price="+ new DecimalFormat(DECIMAL_FORMAT).format(currentBidPrice));
      LOG.info(() ->market.getName()+ " Current ASK price="+ new DecimalFormat(DECIMAL_FORMAT).format(currentAskPrice));

      // Is this the first time the Strategy has been called? If yes, we initialise the OrderState
      // so we can keep
      // track of orders during later trace cycles.
      if (lastOrder == null) {LOG.info(() ->market.getName()+ " First time Strategy has been called - creating new OrderState object.");
        lastOrder = new OrderState();
      }

      // Always handy to log what the last order was during each trace cycle.
      LOG.info(() -> market.getName() + " Last Order was: " + lastOrder);

      // Execute the appropriate algorithm based on the last order type.
      if (lastOrder.type == OrderType.BUY) {
        executeAlgoForWhenLastOrderWasBuy(currentAskPrice);

      } else if (lastOrder.type == OrderType.SELL) {
        executeAlgoForWhenLastOrderWasSell(currentBidPrice, currentAskPrice);

      } else if (lastOrder.type == null) {
        executeAlgoForWhenLastOrderWasNone(currentBidPrice);
      }

    } catch (ExchangeNetworkException e) {
      // Your timeout handling code could go here.
      // We are just going to log it and swallow it, and wait for next trade cycle.
      LOG.error(() ->market.getName()+" Failed to get market orders because Exchange threw network exception. "+ "Waiting until next trade cycle.", e);

    } catch (TradingApiException e) {
      // Your error handling code could go here...
      // We are just going to re-throw as StrategyException for engine to deal with - it will
      // shutdown the bot.
      LOG.error(
              market.getName()
                      + " Failed to get market orders because Exchange threw TradingApi exception. "
                      + "Telling Trading Engine to shutdown bot!",
              e);
      throw new StrategyException(e);
    }
  }

  /**
   * Algo for executing when the Trading Strategy is invoked for the first time. We start off with a
   * buy order at current BID price.
   *
   * @param currentBidPrice the current market BID price.
   * @throws StrategyException if an unexpected exception is received from the Exchange Adapter.
   *     Throwing this exception indicates we want the Trading Engine to shutdown the bot.
   */
  private void executeAlgoForWhenLastOrderWasNone(BigDecimal currentBidPrice)
          throws StrategyException {
    LOG.info("Prima volta che il bot è avviato, piazzo un nuovo ordine al prezzo stabilito");

    try {

      final BigDecimal amountOfBaseCurrencyToBuy = getAmountOfBaseCurrencyToBuyForGivenCounterCurrencyAmount(counterCurrencyBuyOrderAmount);

      // Send the order to the exchange

      LOG.info("Sto per inviare un ordine di {} {} ovvero di {} {} al prezzo di: {}",new DecimalFormat(DECIMAL_FORMAT).format(amountOfBaseCurrencyToBuy),market.getBaseCurrency(),new DecimalFormat(DECIMAL_FORMAT).format(counterCurrencyBuyOrderAmount),market.getCounterCurrency(),new DecimalFormat(DECIMAL_FORMAT).format(currentBidPrice));

      lastOrder.id = tradingApi.createOrder(market.getId(), OrderType.BUY, amountOfBaseCurrencyToBuy, currentBidPrice);

      LOG.info("Ordine eseguito con id: {} comprati {} {} al prezzo di {} {}",lastOrder.id,amountOfBaseCurrencyToBuy,market.getBaseCurrency(),currentBidPrice,market.getCounterCurrency());

      // update last order details
      lastOrder.price = currentBidPrice;
      lastOrder.type = OrderType.BUY;
      lastOrder.amount = amountOfBaseCurrencyToBuy;

    } catch (ExchangeNetworkException e) {
      // Your timeout handling code could go here, e.g. you might want to check if the order
      // actually made it to the exchange? And if not, resend it...
      // We are just going to log it and swallow it, and wait for next trade cycle.
      LOG.error(() ->market.getName()+ " Initial order to BUY base currency failed because Exchange threw network exception. Waiting until next trade cycle.",e);

    } catch (TradingApiException e) {
      // Your error handling code could go here...
      // We are just going to re-throw as StrategyException for engine to deal with - it will
      // shutdown the bot.
      LOG.error(() ->market.getName()+ " Initial order to BUY base currency failed because Exchange threw TradingApi exception. Telling Trading Engine to shutdown bot!",e);
      throw new StrategyException(e);
    }
  }






  /**
   * Algo for executing when last order we placed on the exchanges was a BUY.
   *
   * <p>If last buy order filled, we try and sell at a profit.
   *
   * @throws StrategyException if an unexpected exception is received from the Exchange Adapter.
   *     Throwing this exception indicates we want the Trading Engine to shutdown the bot.
   */
  private void executeAlgoForWhenLastOrderWasBuy(BigDecimal currentAskPrice) throws StrategyException {


    try {
      List<OpenOrder> myOrders = tradingApi.getYourOpenOrders(market.getId());
      boolean isFilled = true;
      for (OpenOrder myOrder : myOrders) {
        if (myOrder.getId().equals(lastOrder.id)) {
          isFilled=false;
          LOG.info("Ordine con id: {} non ancora fillato, aspetto...",lastOrder.id);
          break;

        }
      }


      if(isFilled) {
        LOG.info(() -> market.getName() + " Ultimo ordine [" + lastOrder.id + "] al prezzo di [" + new DecimalFormat(DECIMAL_FORMAT).format(lastOrder.price) + "]");

/**
 * Qui devo verificare in che caso siamo, 3 scenari possibili
 * 1) PERDITA: se stiamo andando sotto come prezzo rispetto a quando abbiamo comprato bisogna vendere entro la percentuale di perdita impostata nelle properties,
 * - Scenario 2-3 sono di GUADAGNO
 *    -2) il prezzo sta salendo ma non abbiamo ancora raggiunto il target di guadagno % impostato
 *    -3A) Abbiamo raggiunto il target e stiamo salendo ancora, in questo caso stiamo dietro al massimo fino a quando raggiunge la percentuale di perdita max
 *         impostata nelle propeties
 *    -3B) Abbiamo raggiunto il target AND siamo sotto di quella % rispetto al massimo raggiunto, vendiamo
 *
 *
 */


        /**
         * Scenario 1:
         */
        if (currentAskPrice.compareTo(lastOrder.price) < 0) {
          LOG.warn("Scenario 1) : perdita");

          //prezzo al quale abbiamo comprato - minima perdita/100 * prezzo al quale abbiamo comprato
          BigDecimal stopLossPrice = lastOrder.price.subtract(perditaMassimaPerc.multiply(lastOrder.price));
          LOG.info("Prezzo di perdita massima è: [{}] al momento siamo a: [{}]", new DecimalFormat(DECIMAL_FORMAT).format(stopLossPrice), currentAskPrice);

          if (stopLossPrice.compareTo(currentAskPrice) < 0) {
            LOG.info("Ok non è ancora il momento di vendere, la perdita è inferiore a quanto configurato");
          } else {
            LOG.info("La perdita ha superato il limite impostato..");


            LOG.info("Sto per inviare un ordine di vendita al prezzo: {}, di {} {}", new DecimalFormat(DECIMAL_FORMAT).format(currentAskPrice), lastOrder.amount, market.getCounterCurrency());
            lastOrder.id = tradingApi.createOrder(market.getId(), OrderType.SELL, lastOrder.amount, currentAskPrice);
            LOG.info(() -> market.getName() + " Ordine inviato. ID: " + lastOrder.id);
            BigDecimal pedita = (currentAskPrice.subtract(lastOrder.price)).multiply(lastOrder.amount);
            LOG.info("perdita con quest'azione abbiamo perso: {} {}", new DecimalFormat(DECIMAL_FORMAT).format(pedita), market.getCounterCurrency());
            lastOrder.type = OrderType.SELL;

          }


        } else if (currentAskPrice.compareTo(lastOrder.price) > 0) {


          //prezzo al quale abbiamo comprato - minima perdita/100 * prezzo al quale abbiamo comprato
          BigDecimal guadagnoMinimo = lastOrder.price.add(minimoGuadagnoPerc.multiply(lastOrder.price));
          LOG.info("Il prezzo minimo di guadagno è: {} al momento siamo a: {}", new DecimalFormat(DECIMAL_FORMAT).format(guadagnoMinimo), currentAskPrice);

          if (guadagnoMinimo.compareTo(currentAskPrice) > 0) {
            LOG.info("Scenario 2) Target di guadagno % non raggiunto");
          } else {
            LOG.info("Scenario 3) prezzo massimo raggiunto al momento : {}", highiestPriceReached);
            if (highiestPriceReached.compareTo(currentAskPrice) < 0) {
              highiestPriceReached = currentAskPrice;
              LOG.info("Nuovo massimo, valore aggiornato a: {}", currentAskPrice);
            } else {
              LOG.info("Prezzo al di sotto del massimo, verifiche condizioni di vendita");
              BigDecimal prezzoSottoMinimo = highiestPriceReached.subtract(perditaSottoMassimoPerc.multiply(highiestPriceReached));
              LOG.info("Il prezzo al quale attualmente dobbiamo vendere è: {} mentre il prezzo attuale è {} ", new DecimalFormat(DECIMAL_FORMAT).format(prezzoSottoMinimo), currentAskPrice);
              if (currentAskPrice.compareTo(prezzoSottoMinimo) < 0) {
                LOG.info("Scenario 4), vendita");
                LOG.info("Sto per inviare un ordine di vendita al prezzo: {}, di {} {}", new DecimalFormat(DECIMAL_FORMAT).format(currentAskPrice), lastOrder.amount, market.getCounterCurrency());
                lastOrder.id = tradingApi.createOrder(market.getId(), OrderType.SELL, lastOrder.amount, currentAskPrice);
                LOG.info(() -> market.getName() + " Ordine inviato. ID: " + lastOrder.id);
                BigDecimal guadagno = (currentAskPrice.subtract(lastOrder.price)).multiply(lastOrder.amount);
                LOG.info("Guadagno con quest'azione abbiamo guadagnato: {} {}", new DecimalFormat(DECIMAL_FORMAT).format(guadagno), market.getCounterCurrency());
                lastOrder.type = OrderType.SELL;
                lastOrder.amount = lastOrder.amount;
              } else {
                LOG.info("Non sussistono le condizioni per vendere, sempre scenario 3)");
              }
            }
          }
        }

      }
    } catch (ExchangeNetworkException e) {
      // Your timeout handling code could go here, e.g. you might want to check if the order
      // actually
      // made it to the exchange? And if not, resend it...
      // We are just going to log it and swallow it, and wait for next trade cycle.
      LOG.error(() -> market.getName() + " New Order to SELL base currency failed because Exchange threw network " + "exception. Waiting until next trade cycle. Last Order: " + lastOrder, e);

    } catch (TradingApiException e) {
      // Your error handling code could go here...
      // We are just going to re-throw as StrategyException for engine to deal with - it will
      // shutdown the bot.
      LOG.error(
              () ->
                      market.getName()
                              + " New order to SELL base currency failed because Exchange threw TradingApi "
                              + "exception. Telling Trading Engine to shutdown bot! Last Order: "
                              + lastOrder,
              e);
      throw new StrategyException(e);
    }
  }

  /**
   * Algo for executing when last order we placed on the exchange was a SELL.
   *
   * <p>If last sell order filled, we send a new buy order to the exchange.
   *
   * @param currentBidPrice the current market BID price.
   * @param currentAskPrice the current market ASK price.
   * @throws StrategyException if an unexpected exception is received from the Exchange Adapter.
   *     Throwing this exception indicates we want the Trading Engine to shutdown the bot.
   */
  private void executeAlgoForWhenLastOrderWasSell(BigDecimal currentBidPrice, BigDecimal currentAskPrice) throws StrategyException {

    try {
      List<OpenOrder> myOrders = null;
      myOrders = tradingApi.getYourOpenOrders(market.getId());

      boolean isFilled = true;
      for (OpenOrder myOrder : myOrders) {
        if (myOrder.getId().equals(lastOrder.id)) {
          isFilled=false;
          LOG.info("Ordine con id: {} non ancora fillato, aspetto...",lastOrder.id);
          break;

        }
      }


      if(isFilled) {
        LOG.info(() -> market.getName() + " Ultimo ordine [" + lastOrder.id + "] al prezzo di [" + new DecimalFormat(DECIMAL_FORMAT).format(lastOrder.price) + "]");
        LOG.info(() ->"Niente da fare qui, quello che è stato venduto è stato venduto il bot al momento gira a vuoto");
      }
    } catch (ExchangeNetworkException e) {
      // Your timeout handling code could go here, e.g. you might want to check if the order
      // actually
      // made it to the exchange? And if not, resend it...
      // We are just going to log it and swallow it, and wait for next trade cycle.
      LOG.error(() -> market.getName() + " New Order to SELL base currency failed because Exchange threw network " + "exception. Waiting until next trade cycle. Last Order: " + lastOrder, e);

    } catch (TradingApiException e) {
      // Your error handling code could go here...
      // We are just going to re-throw as StrategyException for engine to deal with - it will
      // shutdown the bot.
      LOG.error(
              () ->
                      market.getName()
                              + " New order to SELL base currency failed because Exchange threw TradingApi "
                              + "exception. Telling Trading Engine to shutdown bot! Last Order: "
                              + lastOrder,
              e);
      throw new StrategyException(e);
    }


  }

  /**
   * Returns amount of base currency (BTC) to buy for a given amount of counter currency (USD) based
   * on last market trade price.
   *
   * @param amountOfCounterCurrencyToTrade the amount of counter currency (USD) we have to trade
   *     (buy) with.
   * @return the amount of base currency (BTC) we can buy for the given counter currency (USD)
   *     amount.
   * @throws TradingApiException if an unexpected error occurred contacting the exchange.
   * @throws ExchangeNetworkException if a request to the exchange has timed out.
   */
  private BigDecimal getAmountOfBaseCurrencyToBuyForGivenCounterCurrencyAmount(
          BigDecimal amountOfCounterCurrencyToTrade)
          throws TradingApiException, ExchangeNetworkException {

    LOG.info(
            () ->
                    market.getName()
                            + " Calculating amount of base currency (BTC) to buy for amount of counter "
                            + "currency "
                            + new DecimalFormat(DECIMAL_FORMAT).format(amountOfCounterCurrencyToTrade)
                            + " "
                            + market.getCounterCurrency());

    // Fetch the last trade price
    final BigDecimal lastTradePriceInUsdForOneBtc = tradingApi.getLatestMarketPrice(market.getId());
    LOG.info(
            () ->
                    market.getName()
                            + " Last trade price for 1 "
                            + market.getBaseCurrency()
                            + " was: "
                            + new DecimalFormat(DECIMAL_FORMAT).format(lastTradePriceInUsdForOneBtc)
                            + " "
                            + market.getCounterCurrency());

    /*
     * Most exchanges (if not all) use 8 decimal places and typically round in favour of the
     * exchange. It's usually safest to round down the order quantity in your calculations.
     */
    final BigDecimal amountOfBaseCurrencyToBuy =
            amountOfCounterCurrencyToTrade.divide(
                    lastTradePriceInUsdForOneBtc, 8, RoundingMode.HALF_DOWN);

    LOG.info(
            () ->
                    market.getName()
                            + " Amount of base currency ("
                            + market.getBaseCurrency()
                            + ") to BUY for "
                            + new DecimalFormat(DECIMAL_FORMAT).format(amountOfCounterCurrencyToTrade)
                            + " "
                            + market.getCounterCurrency()
                            + " based on last market trade price: "
                            + amountOfBaseCurrencyToBuy);

    return amountOfBaseCurrencyToBuy;
  }

  /**
   * Init della strategia
   * @param config
   */
  private void getConfigForStrategy(StrategyConfig config) {

    // Get counter currency buy amount...
    final String counterCurrencyBuyOrderAmountFromConfigAsString = config.getConfigItem("counter-currency-buy-order-amount");
    if (counterCurrencyBuyOrderAmountFromConfigAsString == null) {
      throw new IllegalArgumentException("Mandatory counter-currency-buy-order-amount value missing in strategy.xml config.");
    }
    LOG.info(() ->"<counter-currency-buy-order-amount> from config is: "+ counterCurrencyBuyOrderAmountFromConfigAsString);
    counterCurrencyBuyOrderAmount = new BigDecimal(counterCurrencyBuyOrderAmountFromConfigAsString);
    LOG.info(() -> "counterCurrencyBuyOrderAmount: " + counterCurrencyBuyOrderAmount);

    //guadagno minimo
    final String guadagnoMinimoString = config.getConfigItem("guadagno-minimo");
    if (guadagnoMinimoString == null) {
      throw new IllegalArgumentException("Mandatory guadagno-minimo value missing in strategy.xml config.");
    }
    LOG.info(() ->"<guadagno-minimo> from config is: "+ guadagnoMinimoString);
    BigDecimal minGuadConfig = new BigDecimal(guadagnoMinimoString);
    minimoGuadagnoPerc = minGuadConfig.divide(new BigDecimal(100), 8, RoundingMode.HALF_UP);
    LOG.info(() -> "minimoGuadagno: " + minimoGuadagnoPerc);


    //perdita massima
    final String perditaMassimaString = config.getConfigItem("perdita-massima");
    if (perditaMassimaString == null) {
      throw new IllegalArgumentException("Mandatory perdita-massima value missing in strategy.xml config.");
    }
    LOG.info(() ->"<perdita-massima> from config is: "+ perditaMassimaString);
    BigDecimal perditaMaxConfig = new BigDecimal(perditaMassimaString);
    perditaMassimaPerc = perditaMaxConfig.divide(new BigDecimal(100), 8, RoundingMode.HALF_UP);
    LOG.info(() -> "perditaMassima: " + perditaMassimaPerc);


    //perdita sotto massimo
    final String perditaSottoMassimoString = config.getConfigItem("perdita-sotto-massimo");
    if (perditaSottoMassimoString == null) {
      throw new IllegalArgumentException("Mandatory perdita-sotto-massimo value missing in strategy.xml config.");
    }
    LOG.info(() ->"<perdita-sotto-massimo> from config is: "+ perditaSottoMassimoString);
    BigDecimal perditaSottoMax = new BigDecimal(perditaSottoMassimoString);
    perditaSottoMassimoPerc = perditaSottoMax.divide(new BigDecimal(100), 8, RoundingMode.HALF_UP);
    LOG.info(() -> "perditaSottoMassimo: " + perditaSottoMassimoPerc);





  }

  /**
   * Models the state of an Order placed on the exchange.
   *
   * <p>Typically, you would maintain order state in a database or use some other persistence method
   * to recover from restarts and for audit purposes. In this example, we are storing the state in
   * memory to keep it simple.
   */
  private static class OrderState {

    /** Id - default to null. */
    private String id = null;

    /**
     * Type: buy/sell. We default to null which means no order has been placed yet, i.e. we've just
     * started!
     */
    private OrderType type = null;

    /** Price to buy/sell at - default to zero. */
    private BigDecimal price = BigDecimal.ZERO;

    private BigDecimal highiestPriceReached = BigDecimal.ZERO;

    /** Number of units to buy/sell - default to zero. */
    private BigDecimal amount = BigDecimal.ZERO;



    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
              .add("id", id)
              .add("type", type)
              .add("price", price)
              .add("amount", amount)
              .toString();
    }
  }
}
