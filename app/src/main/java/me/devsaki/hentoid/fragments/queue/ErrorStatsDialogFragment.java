package me.devsaki.hentoid.fragments.queue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogUtil;
import me.devsaki.hentoid.util.ToastUtil;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 11/2018
 * Info dialog for download errors details
 */
public class ErrorStatsDialogFragment extends DialogFragment {

    private static final String ID = "ID";

    private TextView details;
    private int previousNbErrors;
    private long currentId;
    private View rootView;

    public static void invoke(Fragment parent, long id) {
        ErrorStatsDialogFragment fragment = new ErrorStatsDialogFragment();

        Bundle args = new Bundle();
        args.putLong(ID, id);
        fragment.setArguments(args);

        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Dialog);
        fragment.show(parent.getChildFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_queue_errors, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        this.rootView = rootView;

        if (getArguments() != null) {
            details = requireViewById(rootView, R.id.stats_details);
            details.setText(R.string.downloads_loading);

            previousNbErrors = 0;
            long id = getArguments().getLong(ID, 0);
            currentId = id;
            if (id > 0) updateStats(id);
        }

        View openLogButton = requireViewById(rootView, R.id.open_log_btn);
        openLogButton.setOnClickListener(v -> this.showErrorLog());

        View copyLogButton = requireViewById(rootView, R.id.copy_log_btn);
        copyLogButton.setOnClickListener(v -> this.copyErrorLog());
    }

    private void updateStats(long contentId) {
        List<ErrorRecord> errors = ObjectBoxDB.getInstance(getContext()).selectErrorRecordByContentId(contentId);
        Map<ErrorType, Integer> errorsByType = new HashMap<>();

        for (ErrorRecord error : errors) {
            if (errorsByType.containsKey(error.type)) {
                Integer nbErrorsObj = errorsByType.get(error.type);
                int nbErrors = (null == nbErrorsObj) ? 0 : nbErrorsObj;
                errorsByType.put(error.type, ++nbErrors);
            } else {
                errorsByType.put(error.type, 1);
            }
        }

        StringBuilder detailsStr = new StringBuilder();

        for (ErrorType type : errorsByType.keySet()) {
            detailsStr.append(type.getName()).append(" : ");
            detailsStr.append(errorsByType.get(type));
            detailsStr.append(System.getProperty("line.separator"));
        }

        details.setText(detailsStr.toString());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        if (event.eventType == DownloadEvent.EV_COMPLETE) {
            details.setText(R.string.download_complete);
            previousNbErrors = 0;
        } else if (event.eventType == DownloadEvent.EV_CANCEL) {
            details.setText(R.string.download_cancelled);
            previousNbErrors = 0;
        } else if ((event.eventType == DownloadEvent.EV_PROGRESS)
                && (event.pagesKO > previousNbErrors)
                && (event.content != null)) {
            currentId = event.content.getId();
            previousNbErrors = event.pagesKO;
            updateStats(currentId);
        }
    }

    private LogUtil.LogInfo createLog() {
        CollectionDAO dao = new ObjectBoxDAO(getContext());
        Content content = dao.selectContent(currentId);
        if (null == content) {
            Snackbar snackbar = Snackbar.make(rootView, R.string.content_not_found, BaseTransientBottomBar.LENGTH_LONG);
            snackbar.show();
            return new LogUtil.LogInfo();
        }

        List<String> log = new ArrayList<>();

        LogUtil.LogInfo errorLogInfo = new LogUtil.LogInfo();
        errorLogInfo.logName = "Error";
        errorLogInfo.fileName = "error_log" + content.getId();
        errorLogInfo.noDataMessage = "No error detected.";
        errorLogInfo.log = log;

        List<ErrorRecord> errorLog = content.getErrorLog();
        if (errorLog != null) {
            log.add("Error log for " + content.getTitle() + " [" + content.getUniqueSiteId() + "@" + content.getSite().getDescription() + "] : " + errorLog.size() + " errors");
            for (ErrorRecord e : errorLog) log.add(e.toString());
        }

        return errorLogInfo;
    }

    private void showErrorLog() {
        ToastUtil.toast(R.string.redownload_generating_log_file);

        LogUtil.LogInfo logInfo = createLog();
        File logFile = LogUtil.writeLog(requireContext(), logInfo);
        if (logFile != null) FileHelper.openFile(requireContext(), logFile);
    }

    private void copyErrorLog() {
        LogUtil.LogInfo logInfo = createLog();
        if (Helper.copyPlainTextToClipboard(requireActivity(), LogUtil.buildLog(logInfo))) {
            Snackbar snackbar = Snackbar.make(rootView, R.string.redownload_log_clipboard, BaseTransientBottomBar.LENGTH_LONG);
            snackbar.show();
        }
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }
}