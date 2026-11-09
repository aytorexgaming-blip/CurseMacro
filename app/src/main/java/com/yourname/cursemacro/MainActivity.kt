package com.yourname.cursemacro

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ArrayAdapter<TapStep>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val inputX = findViewById<EditText>(R.id.inputX)
        val inputY = findViewById<EditText>(R.id.inputY)
        val inputDelay = findViewById<EditText>(R.id.inputDelay)
        val stepList = findViewById<ListView>(R.id.stepList)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, MacroStore.steps)
        stepList.adapter = adapter

        findViewById<Button>(R.id.buttonAddStep).setOnClickListener {
            val x = inputX.text.toString().toFloatOrNull()
            val y = inputY.text.toString().toFloatOrNull()
            val delay = inputDelay.text.toString().toLongOrNull()

            if (x == null || y == null || delay == null) {
                Toast.makeText(this, "Fill in X, Y and delay first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            MacroStore.steps.add(TapStep(x, y, delay))
            adapter.notifyDataSetChanged()
            inputX.text.clear()
            inputY.text.clear()
            inputDelay.text.clear()
        }

        findViewById<Button>(R.id.buttonEnableService).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.buttonStart).setOnClickListener {
            val service = MacroAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, "Turn on the Accessibility Service first (button 1 above)", Toast.LENGTH_LONG).show()
            } else if (MacroStore.steps.isEmpty()) {
                Toast.makeText(this, "Add at least one tap step first", Toast.LENGTH_SHORT).show()
            } else {
                service.startLoop()
                Toast.makeText(this, "Loop started - switch to the game now", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.buttonStop).setOnClickListener {
            MacroAccessibilityService.instance?.stopLoop()
            Toast.makeText(this, "Loop stopped", Toast.LENGTH_SHORT).show()
        }
    }
}
