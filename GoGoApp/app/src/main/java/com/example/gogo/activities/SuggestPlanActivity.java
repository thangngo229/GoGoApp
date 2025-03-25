package com.example.gogo.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gogo.R;
import com.example.gogo.adapters.SuggestedExerciseAdapter;
import com.example.gogo.models.Exercise;
import com.example.gogo.models.ExercisePlan;
import com.example.gogo.repository.ExercisePlanRepository;
import com.example.gogo.repository.ExerciseRepository;
import com.example.gogo.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;

public class SuggestPlanActivity extends AppCompatActivity implements SuggestedExerciseAdapter.OnAddToPlanClickListener {
    private EditText targetCaloriesInput;
    private Button btnSuggestPlan;
    private RecyclerView recyclerView;
    private SuggestedExerciseAdapter adapter;
    private int userId;
    private ExercisePlanRepository planRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suggest_plan);

        Log.d("SuggestPlanActivity", "onCreate called");

        userId = getIntent().getIntExtra("USER_ID", -1);
        Log.d("SuggestPlanActivity", "Received userId: " + userId);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Gợi ý kế hoạch");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        planRepo = new ExercisePlanRepository(this);
        targetCaloriesInput = findViewById(R.id.target_calories);
        btnSuggestPlan = findViewById(R.id.btn_suggest_plan);
        recyclerView = findViewById(R.id.recycler_view_suggested_exercises);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setNestedScrollingEnabled(false); // Đảm bảo RecyclerView không cuộn bên trong

        btnSuggestPlan.setOnClickListener(v -> suggestPlan());
    }

    private void suggestPlan() {
        String targetCaloriesStr = targetCaloriesInput.getText().toString();
        if (targetCaloriesStr.isEmpty()) {
            targetCaloriesInput.setError("Vui lòng nhập mục tiêu calories");
            return;
        }

        int targetCalories;
        try {
            targetCalories = Integer.parseInt(targetCaloriesStr);
            if (targetCalories <= 0) {
                targetCaloriesInput.setError("Mục tiêu calories phải lớn hơn 0");
                return;
            }
        } catch (NumberFormatException e) {
            targetCaloriesInput.setError("Vui lòng nhập số hợp lệ");
            return;
        }

        List<SuggestedExercise> suggestedExercises = generateSuggestedExercises(targetCalories);
        if (suggestedExercises.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Không thể gợi ý")
                    .setMessage("Không có bài tập nào phù hợp với mục tiêu calories của bạn.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        int totalCaloriesSuggested = 0;
        for (SuggestedExercise suggested : suggestedExercises) {
            totalCaloriesSuggested += suggested.caloriesBurned;
        }

        if (totalCaloriesSuggested < targetCalories) {
            new AlertDialog.Builder(this)
                    .setTitle("Cảnh báo")
                    .setMessage("Chỉ có thể gợi ý " + totalCaloriesSuggested + " kcal, không đạt mục tiêu " + targetCalories + " kcal.")
                    .setPositiveButton("OK", (dialog, which) -> displaySuggestedExercises(suggestedExercises))
                    .setNegativeButton("Hủy", null)
                    .show();
        } else {
            displaySuggestedExercises(suggestedExercises);
        }
    }

    private List<SuggestedExercise> generateSuggestedExercises(int targetCalories) {
        ExerciseRepository repo = new ExerciseRepository(this);
        List<Exercise> allExercises = repo.getAllExercises();
        List<SuggestedExercise> suggestedExercises = new ArrayList<>();

        // Số lượng bài tập tối thiểu muốn đề xuất
        final int MIN_EXERCISES = 3;

        // Nếu không đủ bài tập, trả về danh sách rỗng
        if (allExercises.size() < MIN_EXERCISES) {
            Log.d("SuggestPlanActivity", "Not enough exercises available. Required: " + MIN_EXERCISES + ", Available: " + allExercises.size());
            return suggestedExercises;
        }

        int remainingCalories = targetCalories;
        int exercisesToSuggest = Math.min(MIN_EXERCISES, allExercises.size()); // Đảm bảo không vượt quá số bài tập có sẵn

        // Phân bổ calories cho từng bài tập
        int caloriesPerExercise = targetCalories / exercisesToSuggest; // Phân bổ đều calories cho mỗi bài tập

        for (int i = 0; i < exercisesToSuggest && remainingCalories > 0; i++) {
            Exercise exercise = allExercises.get(i);
            double caloriesPerMinute = exercise.getEnergyConsumed();
            if (caloriesPerMinute <= 0) {
                continue;
            }

            // Nếu đây là bài tập cuối cùng trong danh sách cần đề xuất, sử dụng toàn bộ remainingCalories
            int caloriesToBurn = (i == exercisesToSuggest - 1) ? remainingCalories : Math.min(remainingCalories, caloriesPerExercise);
            int minutes = (int) Math.ceil(caloriesToBurn / caloriesPerMinute);

            if (minutes > 0) {
                int caloriesBurned = (int) (caloriesPerMinute * minutes);
                suggestedExercises.add(new SuggestedExercise(exercise, minutes, caloriesBurned));
                remainingCalories -= caloriesBurned;
            }
        }

        // Nếu vẫn còn remainingCalories, phân bổ thêm cho bài tập cuối cùng
        if (remainingCalories > 0 && !suggestedExercises.isEmpty()) {
            SuggestedExercise lastExercise = suggestedExercises.get(suggestedExercises.size() - 1);
            double caloriesPerMinute = lastExercise.exercise.getEnergyConsumed();
            int additionalMinutes = (int) Math.ceil(remainingCalories / caloriesPerMinute);
            lastExercise.minutes += additionalMinutes;
            lastExercise.caloriesBurned += (int) (caloriesPerMinute * additionalMinutes);
            remainingCalories = 0; // Đảm bảo remainingCalories được cập nhật
        }

        // Nếu vẫn còn remainingCalories, ghi log để debug
        if (remainingCalories > 0) {
            Log.d("SuggestPlanActivity", "Not enough exercises to meet target calories. Remaining: " + remainingCalories);
        }

        return suggestedExercises;
    }

    private void displaySuggestedExercises(List<SuggestedExercise> suggestedExercises) {
        adapter = new SuggestedExerciseAdapter(suggestedExercises, this);
        recyclerView.setAdapter(adapter);
        Log.d("SuggestPlanActivity", "Suggested exercises displayed: " + suggestedExercises.size());
    }

    @Override
    public void onAddToPlanClick(SuggestedExercise suggestedExercise) {
        List<String> existingPlanNames = planRepo.getPlanNamesByUserId(userId);
        List<String> planOptions = new ArrayList<>();
        planOptions.add("Tạo kế hoạch mới...");
        if (existingPlanNames.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Thông báo")
                    .setMessage("Bạn chưa có kế hoạch nào. Vui lòng tạo kế hoạch mới.")
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            planOptions.addAll(existingPlanNames);
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_plan, null);
        Spinner planSpinner = dialogView.findViewById(R.id.plan_spinner);
        EditText planNameInput = dialogView.findViewById(R.id.plan_name_input);
        TextView durationText = dialogView.findViewById(R.id.duration_text);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, planOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        planSpinner.setAdapter(spinnerAdapter);

        durationText.setText("Thời gian: " + suggestedExercise.minutes + " phút");

        planSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    planNameInput.setVisibility(View.VISIBLE);
                } else {
                    planNameInput.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                planNameInput.setVisibility(View.VISIBLE);
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("Thêm vào kế hoạch")
                .setView(dialogView)
                .setPositiveButton("Thêm", (dialog, which) -> {
                    String selectedPlan = (String) planSpinner.getSelectedItem();
                    String planName;

                    if (selectedPlan.equals("Tạo kế hoạch mới...")) {
                        planName = planNameInput.getText().toString().trim();
                        if (planName.isEmpty()) {
                            planNameInput.setError("Vui lòng nhập tên kế hoạch");
                            return;
                        }
                        // Kiểm tra trùng lặp tên kế hoạch
                        if (existingPlanNames.contains(planName)) {
                            planNameInput.setError("Tên kế hoạch đã tồn tại. Vui lòng chọn tên khác.");
                            return;
                        }
                    } else {
                        planName = selectedPlan;
                    }

                    ExercisePlan plan = new ExercisePlan(
                            0,
                            userId,
                            suggestedExercise.exercise.getExerciseID(),
                            suggestedExercise.minutes,
                            suggestedExercise.caloriesBurned,
                            planName,
                            null
                    );
                    planRepo.addPlan(plan);

                    new AlertDialog.Builder(this)
                            .setTitle("Thành công")
                            .setMessage("Đã thêm " + suggestedExercise.exercise.getExerciseName() + " vào kế hoạch " + planName)
                            .setPositiveButton("OK", null)
                            .show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    public static class SuggestedExercise {
        public Exercise exercise;
        public int minutes;
        public int caloriesBurned;

        SuggestedExercise(Exercise exercise, int minutes, int caloriesBurned) {
            this.exercise = exercise;
            this.minutes = minutes;
            this.caloriesBurned = caloriesBurned;
        }
    }
}