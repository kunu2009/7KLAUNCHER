package com.sevenk.calcvault

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var display: TextView
    private var current = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        display = findViewById(R.id.txtDisplay)
        val buttons = intArrayOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,
            R.id.btnPlus, R.id.btnMinus, R.id.btnMul, R.id.btnDiv,
            R.id.btnDot, R.id.btnClear, R.id.btnDel, R.id.btnEquals
        )
        for (id in buttons) findViewById<Button>(id).setOnClickListener { onButtonPressed(it as Button) }
    }

    private fun onButtonPressed(b: Button) {
        when (b.id) {
            R.id.btnClear -> { current.clear(); display.text = "" }
            R.id.btnDel -> { if (current.isNotEmpty()) { current.deleteCharAt(current.length - 1); display.text = current.toString() } }
            R.id.btnEquals -> onEquals()
            else -> {
                current.append(b.text)
                display.text = current.toString()
            }
        }
    }

    private fun onEquals() {
        // Disguised passcode check: if current equals saved passcode, open Vault
        val prefs = getSharedPreferences("calcvault_prefs", MODE_PRIVATE)
        val pass = prefs.getString("passcode", "1234")
        if (current.toString() == pass) {
            startActivity(Intent(this, VaultActivity::class.java))
            current.clear()
            display.text = "0"
            return
        }
        // Otherwise attempt to evaluate simple expression safely
        val result = try { evalBasic(current.toString()) } catch (_: Throwable) { null }
        display.text = result?.toString() ?: "Error"
        current.clear()
    }

    // Very basic expression evaluator: supports + - * / and decimal numbers
    private fun evalBasic(expr: String): Double {
        // Tokenize
        val tokens = mutableListOf<String>()
        var num = StringBuilder()
        fun flush() { if (num.isNotEmpty()) { tokens.add(num.toString()); num = StringBuilder() } }
        for (c in expr.replace(" ", "")) {
            if (c.isDigit() || c == '.') num.append(c) else if ("+-*/".contains(c)) { flush(); tokens.add(c.toString()) } else throw IllegalArgumentException("bad char")
        }
        flush()
        // Shunting-yard lite: first * and /
        var i = 0
        val stack = mutableListOf<String>()
        while (i < tokens.size) {
            val t = tokens[i]
            if (t == "*" || t == "/") {
                val a = stack.removeLast().toDouble()
                val b = tokens[i + 1].toDouble()
                val r = if (t == "*") a * b else a / b
                stack.add(r.toString())
                i += 2
            } else {
                stack.add(t); i++
            }
        }
        // Then + and -
        var res = stack[0].toDouble()
        i = 1
        while (i < stack.size) {
            val op = stack[i]
            val b = stack[i + 1].toDouble()
            res = when (op) { "+" -> res + b; "-" -> res - b; else -> throw IllegalStateException() }
            i += 2
        }
        return res
    }
}
