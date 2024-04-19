package com.demo.killbillbraintreedemokotlinstudy.controller

import com.demo.killbillbraintreedemokotlinstudy.service.CheckoutService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.math.BigDecimal

@Controller
class CheckoutController(val service: CheckoutService) {
    companion object {
        private val logger = LoggerFactory.getLogger(CheckoutController::class.java)
    }

    @GetMapping("/")
    fun hello(): String {
        return "hello"
    }

    @GetMapping("/test")
    fun root(model: Model): String{
        return "redirect:checkouts";
    }

    @GetMapping("/checekouts")
    fun checkout(model: Model): String{
        val clientToken = service.getBraintreeToken()
        logger.info("Token {}", clientToken)
        model.addAttribute("clientToken", clientToken)
        return "checkouts/new";
    }

    @PostMapping("/checkouts")
    fun postForm(@RequestParam("amount") amount: String, @RequestParam("payment_method_nonce") nonce: String, redirectAttributes: RedirectAttributes): String {
        val decimalAmount = try{
            BigDecimal(amount)
        }catch(e: NumberFormatException) {
            redirectAttributes.addFlashAttribute("errorDetails", "Error: 81503: Amount is an invalid format.")
            return "redirect:checkouts"
        }

        logger.info("Amount: {}, Nonce: {}", amount, nonce)
        service.addPaymentMethodAndChargeCustomer(decimalAmount, nonce)

        return "checkouts/successful"
    }
}