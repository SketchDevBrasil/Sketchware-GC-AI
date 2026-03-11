package mod.sdb.agente;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import pro.sketchware.utility.SketchwareUtil;

public class SdbAgenteBottomSheet extends BottomSheetDialogFragment {

    public interface OnApplyListener {
        void onApply(String instruction);
    }

    private OnApplyListener listener;
    private String contextName = "";
    private boolean isThinking = false;

    private TextView tvContextBadge;
    private ProgressBar progressThinking;
    private TextInputEditText etInstruction;
    private ChipGroup chipGroup;
    private View btnApply;

    public static SdbAgenteBottomSheet newInstance(String contextName, OnApplyListener listener) {
        SdbAgenteBottomSheet fragment = new SdbAgenteBottomSheet();
        fragment.contextName = contextName;
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(pro.sketchware.R.layout.sdb_agente_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvContextBadge = view.findViewById(pro.sketchware.R.id.tv_context_badge);
        progressThinking = view.findViewById(pro.sketchware.R.id.progress_thinking);
        etInstruction = view.findViewById(pro.sketchware.R.id.et_instruction);
        chipGroup = view.findViewById(pro.sketchware.R.id.chip_group_actions);
        btnApply = view.findViewById(pro.sketchware.R.id.btn_apply);

        tvContextBadge.setText("Contexto: " + contextName);

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            Chip chip = view.findViewById(checkedId);
            if (chip != null) {
                etInstruction.setText(chip.getText().toString().replaceAll("⚡ |📝 |🔍 |🧹 ", ""));
            }
        });

        btnApply.setOnClickListener(v -> {
            String text = etInstruction.getText().toString().trim();
            if (!text.isEmpty() && listener != null) {
                setThinking(true);
                listener.onApply(text);
            }
        });
    }

    public void setThinking(boolean thinking) {
        this.isThinking = thinking;
        if (progressThinking != null) {
            progressThinking.setVisibility(thinking ? View.VISIBLE : View.GONE);
            btnApply.setEnabled(!thinking);
            etInstruction.setEnabled(!thinking);
            chipGroup.setEnabled(!thinking);
        }
    }
}
