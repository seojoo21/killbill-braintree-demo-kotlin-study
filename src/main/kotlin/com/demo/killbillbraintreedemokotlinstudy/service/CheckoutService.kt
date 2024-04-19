package com.demo.killbillbraintreedemokotlinstudy.service

import java.math.BigDecimal

interface CheckoutService {
    fun getBraintreeToken(): String
    fun addPaymentMethodAndChargeCustomer(amount:BigDecimal, nonce:String)
}