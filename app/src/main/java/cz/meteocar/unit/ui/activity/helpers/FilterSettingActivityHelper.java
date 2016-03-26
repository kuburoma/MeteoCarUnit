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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cz.meteocar.unit.R;
import cz.meteocar.unit.engine.ServiceManager;
import cz.meteocar.unit.engine.enums.FilterEnum;
import cz.meteocar.unit.engine.enums.NonObdFilterTagEnum;
import cz.meteocar.unit.engine.log.AppLog;
import cz.meteocar.unit.engine.storage.helper.FilterSettingHelper;
import cz.meteocar.unit.engine.storage.helper.ObdPidHelper;
import cz.meteocar.unit.engine.storage.model.FilterSettingEntity;
import cz.meteocar.unit.engine.storage.model.ObdPidEntity;

/**
 * Prepares dialog for editing filter settings.
 */
public class FilterSettingActivityHelper {

    private FilterSettingHelper filterSettingHelper = ServiceManager.getInstance().db.getFilterSettingHelper();
    private ObdPidHelper obdPidHelper = ServiceManager.getInstance().db.getObdPidHelper();

    private View dialogView;
    private Context context;
    private AlertDialog dialog;
    private PreferenceScreen cat;
    private int dialogDataID = 0;

    /**
     * Constructor.
     *
     * @param context    Context of application.
     * @param dialogView view to be used.
     * @param cat        Preference screen used.
     */
    public FilterSettingActivityHelper(Context context, View dialogView, PreferenceScreen cat) {
        this.context = context;
        this.dialogView = dialogView;
        this.cat = cat;
    }

    /**
     * Prepares list of filter settings.
     */
    public void createScreen() {
        cat.removeAll();

        List<FilterSettingEntity> arr = filterSettingHelper.getAll();
        if (arr.size() == 0) {
            cat.addPreference(new Preference(context));
            return;
        }
        for (FilterSettingEntity pid : arr) {

            Log.d(AppLog.LOG_TAG_UI, "Adding filter setting code: " + pid.getTag());

            final int myID = pid.getId();
            Preference btn = new Preference(context);
            btn.setTitle(pid.getTag());
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

    /**
     * Show dialog for filter edit
     *
     * @param id of filter to edit
     */
    private void showDialog(int id) {

        dialogDataID = id;

        FilterSettingEntity filter = filterSettingHelper.get(id);

        if (filter == null) {
            filter = new FilterSettingEntity();
            filter.setId(-1);
            filter.setAlgorithm("");
            filter.setTag("");
            filter.setValue(0.0);
            filter.setActive(false);
        }

        Spinner algorithm = (Spinner) dialogView.findViewById(R.id.dialog_filter_algorithm_edit);
        if (algorithm != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, getNames(FilterEnum.values()));
            if (!"".equals(filter.getAlgorithm())) {
                algorithm.setSelection(adapter.getPosition(filter.getAlgorithm()));
            }
            algorithm.setAdapter(adapter);
        }


        Spinner tag = (Spinner) dialogView.findViewById(R.id.dialog_filter_tag_edit);
        if (tag != null) {
            List<String> possibleTags = getPossibleTags();
            possibleTags.add(filter.getTag());
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, possibleTags);
            if (!"".equals(filter.getTag())) {
                tag.setSelection(adapter.getPosition(filter.getTag()));
            }
            tag.setAdapter(adapter);
        }

        EditText roundingDecimal = (EditText) dialogView.findViewById(R.id.dialog_filter_value_edit);
        if (roundingDecimal != null) {
            roundingDecimal.setText(String.valueOf(filter.getValue()));
        }

        ToggleButton active = (ToggleButton) dialogView.findViewById(R.id.dialog_filter_active_edit);
        if (active != null) {
            active.setChecked(filter.isActive());
        }


        dialog.show();
    }

    /**
     * Init dialog for detail of filter setting.
     */
    public void initDialog() {

        // uděláme builder, nastavíme text a titulek
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getResources().getString(R.string.settings_obd_edit_window_title));
        builder.setView(dialogView);

        dialog = builder
                .setPositiveButton(R.string.settings_obd_edit_btn_cancel, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();


                    }
                }).setNeutralButton(R.string.settings_obd_edit_btn_save, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        // připravíme si objekt
                        FilterSettingEntity obj = new FilterSettingEntity();

                        // id
                        obj.setId(dialogDataID);

                        Spinner algorithm = (Spinner) dialogView.findViewById(R.id.dialog_filter_algorithm_edit);
                        if (algorithm != null) {
                            obj.setAlgorithm(algorithm.getSelectedItem().toString());
                        }

                        Spinner tag = (Spinner) dialogView.findViewById(R.id.dialog_filter_tag_edit);
                        if (tag != null) {
                            obj.setTag(tag.getSelectedItem().toString());
                        }

                        EditText roundingDecimal = (EditText) dialogView.findViewById(R.id.dialog_filter_value_edit);
                        if (roundingDecimal != null) {
                            obj.setValue(Double.valueOf(roundingDecimal.getText().toString()));
                        }

                        ToggleButton active = (ToggleButton) dialogView.findViewById(R.id.dialog_filter_active_edit);
                        if (active != null) {
                            obj.setActive(active.isChecked());
                        }

                        obj.setUpdateTime(System.currentTimeMillis());

                        // uložíme
                        if (filterSettingHelper.save(obj) == 1) {
                            dialog.dismiss();
                        } else {
                            AppLog.p(AppLog.LOG_TAG_DB, "Problem while saving OBD PID form data, incorrect save result");
                        }

                    }
                }).setNegativeButton(R.string.settings_obd_edit_btn_delete, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        // vymažeme
                        AppLog.i(AppLog.LOG_TAG_DB, "Deleting PID");
                        filterSettingHelper.delete(dialogDataID);
                        createScreen();


                    }
                }).setCancelable(true).create();
    }


    protected static <E> List<String> getNames(E[] e) {
        List<String> names = new ArrayList<>();
        for (E value : e) {
            names.add(value.toString());
        }
        return names;
    }


    protected List<String> getPossibleTags() {
        List<FilterSettingEntity> filters = filterSettingHelper.getAll();
        List<ObdPidEntity> pids = obdPidHelper.getAll();

        List<String> possibleTags = new ArrayList<>();
        for (ObdPidEntity pid : pids) {
            possibleTags.add(pid.getTag());
        }
        possibleTags.addAll(getNames(NonObdFilterTagEnum.values()));

        Iterator<String> it = possibleTags.iterator();
        while (it.hasNext()) {
            String tag = it.next();
            for (FilterSettingEntity filter : filters) {
                if (filter.getTag().equals(tag)) {
                    it.remove();
                    break;
                }
            }
        }

        return possibleTags;
    }

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

        // přidáme akci ke tlačíku
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDialog(-1);
            }
        });
    }
}