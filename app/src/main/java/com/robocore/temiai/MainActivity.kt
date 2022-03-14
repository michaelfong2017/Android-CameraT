package com.robocore.temiai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.insets.ProvideWindowInsets
import com.robocore.temiai.ui.MainNavGraph
import com.robocore.temiai.ui.theme.TemiAITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemiAITheme {
                ProvideWindowInsets {
                    Surface(color = MaterialTheme.colors.background) {
                        val navController = rememberNavController()
                        Scaffold(
                            scaffoldState = rememberScaffoldState(),
                        ) {
                            MainNavGraph(
                                navController = navController,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TemiAITheme {
        Greeting("Android")
    }
}

