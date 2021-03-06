package cz.meteocar.unit.ui.activity.helpers;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;

import cz.meteocar.unit.R;
import cz.meteocar.unit.engine.ServiceManager;
import cz.meteocar.unit.engine.log.AppLog;
import cz.meteocar.unit.engine.storage.DatabaseException;
import cz.meteocar.unit.engine.storage.helper.ObdPidHelper;
import cz.meteocar.unit.engine.storage.model.ObdPidEntity;

/**
 * Settings helper for dialog and list of obd pid´s.
 */
public class ObdPidSettingActivityHelper {

    private ObdPidHelper obdPidHelper = ServiceManager.getInstance().getDB().getObdPidHelper();

    private View dialogView;
    private Context context;
    private AlertDialog alertDialog;
    private PreferenceScreen cat;
    private int dialogDataID = 0;
    private boolean validationSuccessful = true;

    /**
     * Constructor.
     *
     * @param context    Context of application.
     * @param dialogView view to be used.
     * @param cat        Preference screen used.
     */
    public ObdPidSettingActivityHelper(Context context, View dialogView, PreferenceScreen cat) {
        this.context = context;
        this.dialogView = dialogView;
        this.cat = cat;
    }

    /**
     * Creates list of all OBD pids from database.
     */
    public void createScreen() {
        cat.removeAll();
        List<ObdPidEntity> arr = obdPidHelper.getAll();
        if (arr.isEmpty()) {
            cat.addPreference(new Preference(context));
            return;
        }
        for (ObdPidEntity pid : arr) {

            final int myID = pid.getId();
            Preference btn = new Preference(context);
            btn.setTitle(pid.getName());
            btn.setIcon(R.drawable.icon_tacho);
            btn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showDialog(myID);
                    return false;
                }
            });

            cat.addPreference(btn);
        }
    }

    private void showDialog(int id) {
        dialogDataID = id;

        ObdPidEntity pid = obdPidHelper.get(id);

        if (pid == null) {
            pid = new ObdPidEntity();
            pid.setId(-1);
            pid.setTag("");
            pid.setName("");
            pid.setPidCode("");
            pid.setFormula("");
            pid.setMin(0);
            pid.setMax(0);
            pid.setActive(false);
        }

        // name
        EditText editName = (EditText) dialogView.findViewById(R.id.dialog_obd_name_edit);
        if (editName != null) {
            editName.setText(pid.getName());
        }

        // JSON tag
        EditText editTag = (EditText) dialogView.findViewById(R.id.dialog_obd_tag_edit);
        if (editTag != null) {
            editTag.setText(pid.getTag());
        }

        // kód
        EditText editCode = (EditText) dialogView.findViewById(R.id.dialog_obd_code_edit);
        if (editCode != null) {
            editCode.setText(pid.getPidCode());
        }

        // vzorec
        EditText editFormula = (EditText) dialogView.findViewById(R.id.dialog_obd_formula_edit);
        if (editFormula != null) {
            editFormula.setText(pid.getFormula());
        }

        // min
        EditText editMin = (EditText) dialogView.findViewById(R.id.dialog_obd_min_edit);
        if (editMin != null) {
            editMin.setText(String.valueOf(pid.getMin()));
        }

        // max
        EditText editMax = (EditText) dialogView.findViewById(R.id.dialog_obd_max_edit);
        if (editMax != null) {
            editMax.setText(String.valueOf(pid.getMax()));
        }

        // active
        CheckBox active = (CheckBox) dialogView.findViewById(R.id.dialog_obd_enabled);
        active.setEnabled(true);
        if (pid.isActive()) {
            active.setChecked(true);
        } else {
            active.setChecked(false);
        }
        alertDialog.show();
    }

    /**
     * Init´s dialog for settings.
     */
    public void initDialog() {

        createScreen();

        // uděláme builder, nastavíme text a titulek
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getResources().getString(R.string.settings_obd_edit_window_title));
        builder.setView(dialogView);
        builder.setNeutralButton(R.string.settings_obd_edit_btn_cancel, null);
        builder.setPositiveButton(R.string.settings_obd_edit_btn_save, null);
        builder.setNegativeButton(R.string.settings_obd_edit_btn_delete, null);
        builder.setCancelable(true);
        alertDialog = builder.create();

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(DialogInterface dialog) {

                Button neutralButton = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                neutralButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        alertDialog.dismiss();
                    }
                });


                Button positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positiveButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        ObdPidEntity obj = new ObdPidEntity();

                        obj.setId(dialogDataID);

                        validationSuccessful = true;

                        EditText editName = (EditText) dialogView.findViewById(R.id.dialog_obd_name_edit);
                        validate(editName);
                        if (editName != null) {
                            obj.setName(editName.getText().toString());
                        }

                        EditText editTag = (EditText) dialogView.findViewById(R.id.dialog_obd_tag_edit);
                        validate(editTag);
                        if (editTag != null) {
                            String tag = editTag.getText().toString();
                            if (obdPidHelper.getByTag(tag) != null) {
                                editTag.setError("Tag již použitý");
                                validationSuccessful = false;
                            }
                            obj.setTag(tag);
                        }

                        EditText editCode = (EditText) dialogView.findViewById(R.id.dialog_obd_code_edit);
                        validate(editCode);
                        if (editCode != null) {
                            obj.setPidCode(editCode.getText().toString());
                        }

                        EditText editFormula = (EditText) dialogView.findViewById(R.id.dialog_obd_formula_edit);
                        validate(editFormula);
                        if (editFormula != null) {
                            obj.setFormula(editFormula.getText().toString());
                        }

                        EditText editMin = (EditText) dialogView.findViewById(R.id.dialog_obd_min_edit);
                        validate(editMin);
                        if (editMin != null && editMin.getText().toString().trim().length() != 0) {
                            obj.setMin(Integer.parseInt(editMin.getText().toString()));
                        }

                        EditText editMax = (EditText) dialogView.findViewById(R.id.dialog_obd_max_edit);
                        validate(editMax);
                        if (editMax != null && editMax.getText().toString().trim().length() != 0) {
                            obj.setMax(Integer.parseInt(editMax.getText().toString()));
                        }

                        CheckBox active = (CheckBox) dialogView.findViewById(R.id.dialog_obd_enabled);
                        if (active.isEnabled()) {
                            obj.setActive(active.isChecked());
                        }

                        if (!validationSuccessful) {
                            return;
                        }

                        try {
                            obdPidHelper.save(obj);
                        } catch (DatabaseException e) {
                            Log.e(AppLog.LOG_TAG_DB, e.getMessage(), e);
                        }
                        alertDialog.dismiss();
                        createScreen();
                    }
                });

                Button negativeButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                negativeButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        alertDialog.dismiss();

                        obdPidHelper.delete(dialogDataID);
                        createScreen();
                    }
                });
            }
        });
    }

    /**
     * Creates list of obd pid´s.
     *
     * @param myScreen
     */
    public void treeClick(PreferenceScreen myScreen) {
        // připravíme si layout
        RelativeLayout layout = new RelativeLayout(context);
        layout.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT));

        // naplníme obsah
        Button btn = new Button(context, null, android.R.attr.buttonStyleSmall);
        btn.setText(context.getResources().getString(R.string.settings_obd_pids_add));
        btn.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
        ((RelativeLayout.LayoutParams) btn.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        ((RelativeLayout.LayoutParams) btn.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_TOP);
        btn.setBackgroundColor(Color.TRANSPARENT);

        layout.addView(btn);
        TextView txt = new TextView(context);
        txt.setText(context.getResources().getString(R.string.settings_obd_pids_title));
        txt.setTextAppearance(context, android.R.style.TextAppearance_Medium);
        txt.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
        ((RelativeLayout.LayoutParams) txt.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        ((RelativeLayout.LayoutParams) txt.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_TOP);
        int padding = context.getResources().getDimensionPixelOffset(R.dimen.fragment_padding);
        txt.setPadding(padding, padding * 3 / 4, 0, 0);
        layout.addView(txt);

        final Dialog dialog = myScreen.getDialog();

        if (dialog == null) {
            return;
        }
        dialog.getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        dialog.getActionBar().setCustomView(layout);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDialog(-1);
            }
        });
    }

    private void validate(EditText editText) {
        if (editText.getText().toString().trim().length() == 0) {
            editText.setError(context.getResources().getString(R.string.field_required));
            validationSuccessful = false;
        }
    }
}
