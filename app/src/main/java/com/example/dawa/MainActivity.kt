package com.example.dawa

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.dawa.databinding.ActivityMainBinding
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var openFdaApiService: OpenFdaApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRetrofit()

        binding.buttonSearch.setOnClickListener {
            val drugName = binding.editTextDrugName.text.toString().trim()
            if (drugName.isNotEmpty()) {
                searchDrug(drugName)
            } else {
                Toast.makeText(this, "الرجاء إدخال اسم الدواء", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRetrofit() {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.fda.gov/") // openFDA API base URL
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        openFdaApiService = retrofit.create(OpenFdaApiService::class.java)
    }

    private fun searchDrug(drugName: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.cardResults.visibility = View.GONE

        // Search in drug label section for indications, warnings (side effects), and dosage
        val query = "openfda.brand_name:\"$drugName\"+OR+openfda.generic_name:\"$drugName\""
        openFdaApiService.searchDrugLabel(query).enqueue(object : Callback<DrugLabelResponse> {
            override fun onResponse(call: Call<DrugLabelResponse>, response: Response<DrugLabelResponse>) {
                binding.progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    val results = response.body()?.results
                    if (!results.isNullOrEmpty()) {
                        displayDrugInfo(results[0]) // Display info for the first result
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.no_results), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.error_occurred), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<DrugLabelResponse>, t: Throwable) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "${getString(R.string.error_occurred)}: ${t.message}", Toast.LENGTH_LONG).show()
                t.printStackTrace()
            }
        })
    }

    private fun displayDrugInfo(drugLabel: DrugLabel) {
        binding.cardResults.visibility = View.VISIBLE

        binding.textDrugName.text = drugLabel.openfda?.brand_name?.firstOrNull() ?: drugLabel.openfda?.generic_name?.firstOrNull() ?: "غير معروف"

        // Benefits (Indications and Usage)
        binding.textBenefits.text = drugLabel.indications_and_usage?.joinToString("\n\n") ?: "لا توجد معلومات متوفرة."

        // Side Effects (Warnings and Adverse Reactions)
        val warnings = drugLabel.warnings?.joinToString("\n\n")
        val adverseReactions = drugLabel.adverse_reactions?.joinToString("\n\n")
        binding.textSideEffects.text = when {
            !warnings.isNullOrEmpty() && !adverseReactions.isNullOrEmpty() -> "$warnings\n\n$adverseReactions"
            !warnings.isNullOrEmpty() -> warnings
            !adverseReactions.isNullOrEmpty() -> adverseReactions
            else -> "لا توجد معلومات متوفرة."
        }

        // Dosage and Administration
        binding.textDosage.text = drugLabel.dosage_and_administration?.joinToString("\n\n") ?: "لا توجد معلومات متوفرة."

        // When to take (often part of dosage or indications)
        // For simplicity, we'll use dosage info here, or indicate if not found specifically.
        binding.textWhenToTake.text = drugLabel.dosage_and_administration?.joinToString("\n\n") ?: "يرجى استشارة الطبيب أو الصيدلي لتحديد توقيت التناول."
    }
}

// Retrofit Interface
interface OpenFdaApiService {
    @retrofit2.http.GET("drug/label.json")
    fun searchDrugLabel(@retrofit2.http.Query("search") query: String): Call<DrugLabelResponse>
}

// Data Classes for JSON Parsing
data class DrugLabelResponse(
    val results: List<DrugLabel>
)

data class DrugLabel(
    val id: String?,
    @SerializedName("indications_and_usage") val indications_and_usage: List<String>?,
    val warnings: List<String>?,
    @SerializedName("adverse_reactions") val adverse_reactions: List<String>?,
    @SerializedName("dosage_and_administration") val dosage_and_administration: List<String>?,
    val openfda: OpenFda?
)

data class OpenFda(
    val brand_name: List<String>?,
    val generic_name: List<String>?,
    val manufacturer_name: List<String>?
)


