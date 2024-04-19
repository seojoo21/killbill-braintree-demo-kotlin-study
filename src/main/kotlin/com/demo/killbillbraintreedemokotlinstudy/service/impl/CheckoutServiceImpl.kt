package com.demo.killbillbraintreedemokotlinstudy.service.impl

import com.braintreegateway.BraintreeGateway
import com.braintreegateway.CustomerRequest
import com.demo.killbillbraintreedemokotlinstudy.service.CheckoutService
import jakarta.annotation.PostConstruct
import org.killbill.billing.catalog.api.Currency
import org.killbill.billing.client.KillBillClientException
import org.killbill.billing.client.KillBillHttpClient
import org.killbill.billing.client.RequestOptions
import org.killbill.billing.client.RequestOptions.RequestOptionsBuilder
import org.killbill.billing.client.api.gen.AccountApi
import org.killbill.billing.client.api.gen.InvoiceApi
import org.killbill.billing.client.model.InvoiceItems
import org.killbill.billing.client.model.gen.Account
import org.killbill.billing.client.model.gen.InvoiceItem
import org.killbill.billing.client.model.gen.PaymentMethod
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.util.*

@Service
class CheckoutServiceImpl : CheckoutService{

    @Value("\${killbill.client.url}")
    lateinit var killbillClientUrl: String

    @Value("\${killbill.client.disable-ssl-verification}")
    lateinit var killbillClientDisableSSL: String

    @Value("\${killbill.username}")
    lateinit var username: String

    @Value("\${killbill.password}")
    lateinit var password: String

    @Value("\${killbill.api-key}")
    lateinit var apiKey: String

    @Value("\${killbill.api-secret}")
    lateinit var apiSecret: String

    @Value("\${plugin.name}")
    lateinit var pluginName: String

    @Value("\${checkoutUrl}")
    lateinit var checkoutUrl: String

    @Value("\${braintree.environment}")
    lateinit var environment: String

    @Value("\${braintree.merchantId}")
    lateinit var merchantId: String

    @Value("\${braintree.publicKey}")
    lateinit var publicKey: String

    @Value("\${braintree.privateKey}")
    lateinit var privateKey: String

    companion object {
        private const val PROPERTY_BT_CUSTOMER_ID = "bt_customer_id"
        private const val PROPERTY_BT_NONCE = "bt_nonce"
        private val logger = LoggerFactory.getLogger(CheckoutService::class.java)
    }

    private lateinit var accountApi: AccountApi
    private lateinit var invoiceApi: InvoiceApi
    private lateinit var httpClient: KillBillHttpClient
    private val restTemplate: RestTemplate = RestTemplate()
    private lateinit var gateway: BraintreeGateway

    @PostConstruct
    fun init() {
        httpClient = KillBillHttpClient(killbillClientUrl, username, password, apiKey, apiSecret)
        accountApi = AccountApi(httpClient)
        invoiceApi = InvoiceApi(httpClient)
        gateway = BraintreeGateway(environment, merchantId, publicKey, privateKey)
    }

    private fun getOptions(): RequestOptions{
        return RequestOptionsBuilder()
                .withComment("Braintree Demo")
                .withCreatedBy("demo")
                .withReason("Demonstrating Braintree Drop In").build()
    }

    private fun getHeaders(): HttpHeaders{
        val headers = HttpHeaders()
        headers.add("X-Killbill-ApiKey", apiKey)
        headers.add("X-Killbill-ApiSecret", apiSecret)
        headers.add("X-Killbill-CreatedBy", "test")
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBasicAuth(username, password)
        return headers
    }

    override fun getBraintreeToken(): String {
        val token = restTemplate.exchange(checkoutUrl, HttpMethod.GET, HttpEntity<Any>(getHeaders()), String::class.java)
        val tokenStr: String? = token.body
        return tokenStr?.let {
            if(it.startsWith("\"") && it.endsWith("\"")){
                it.substring(1, it.length-1)
            }else {
                it
            }
        } ?: ""
    }

    private fun createBraintreeCustomer(): String {
        val request = CustomerRequest().firstName("John").lastName("Doe")
        val result = gateway.customer().create(request)
        return result.target.id
    }

    @Throws(KillBillClientException::class)
    private fun createKBAccount():Account {
        val body = Account().apply {
            this.email = "john@test.com"
            this.name = "John Doe"
            this.currency = Currency.USD
        }
        return accountApi.createAccount(body, getOptions())
    }

    override fun addPaymentMethodAndChargeCustomer(amount: BigDecimal, nonce: String) {
        try {
            val braintreeCustomerId = createBraintreeCustomer()
            logger.info("Braintree customerId: {}", braintreeCustomerId)

            val account = createKBAccount()
            logger.info("Kill Bill AccountId:{}", account.getAccountId())

            val pluginProperties = mutableMapOf(
                PROPERTY_BT_NONCE to nonce,
                PROPERTY_BT_CUSTOMER_ID to braintreeCustomerId
            )
            val paymentMethod = createKBPaymentMethod(account.accountId, pluginProperties)
            logger.info("Payment Method Id: {}", paymentMethod.paymentMethodId)

            val createdCharges = createExternalCharge(account.accountId, amount)
            logger.info("Invoice Id: {}", createdCharges[0].invoiceId)

        } catch(e: KillBillClientException){
            logger.error("Error while creating account/payment method/external charge", e)
        }
    }

    @Throws(KillBillClientException::class)
    private fun createKBPaymentMethod(accountId: UUID, pluginProperties: Map<String, String>): PaymentMethod {
        val pm = PaymentMethod().apply {
            this.accountId = accountId
            this.pluginName = pluginName
        }

        val paymentMethod = accountApi.createPaymentMethod(accountId, pm, null, pluginProperties, getOptions())
        val nullPluginProperties: Map<String, String>? = null

        accountApi.setDefaultPaymentMethod(accountId, paymentMethod.paymentMethodId, nullPluginProperties, getOptions())

        return paymentMethod
    }

    @Throws(KillBillClientException::class)
    private fun createExternalCharge(accountId: UUID, amount: BigDecimal): InvoiceItems {
        val externalCharge = InvoiceItem().apply {
            this.accountId = accountId
            this.amount = amount
            this.description = "Braintree Demo Charge"
        }

        val externalCharges = InvoiceItems().apply {
            add(externalCharge)
        }

        val nullPluginProperties: Map<String, String>? = null
        return invoiceApi.createExternalCharges(accountId, externalCharges, null, true, nullPluginProperties, getOptions())
    }
}