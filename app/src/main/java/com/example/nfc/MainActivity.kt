package com.example.nfc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.nfc.ui.MainScreen
import com.example.nfc.ui.MainViewModel
import com.example.nfc.ui.theme.NFCTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NFCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kích hoạt phần cứng khi ứng dụng vào foreground
        viewModel.initHardware()
    }

    override fun onPause() {
        super.onPause()
        // Giải phóng nguồn module RFID khi ứng dụng xuống background để tiết kiệm pin
        viewModel.freeHardware()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.freeHardware()
    }
}