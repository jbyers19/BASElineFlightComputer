package com.platypii.baseline.tracks;

import com.platypii.baseline.R;
import com.platypii.baseline.events.SyncEvent;
import android.app.ListFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import java.util.ArrayList;
import java.util.List;

public class TrackListFragment extends ListFragment implements AdapterView.OnItemClickListener {

    private List<TrackFile> trackList;
    private TrackAdapter listAdapter;

    private View tracksEmptyLabel;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.track_list, container, false);
        tracksEmptyLabel = view.findViewById(R.id.tracks_empty);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Initialize the ListAdapter
        trackList = new ArrayList<>();
        listAdapter = new TrackAdapter(this.getActivity(), trackList);
        setListAdapter(listAdapter);
        getListView().setOnItemClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Update the ListAdapter
        trackList.clear();
        trackList.addAll(TrackFiles.getTracks(this.getActivity()));
        listAdapter.notifyDataSetChanged();

        // Handle no-tracks case
        if(listAdapter.isEmpty()) {
            tracksEmptyLabel.setVisibility(View.VISIBLE);
        } else {
            tracksEmptyLabel.setVisibility(View.GONE);
        }

        // Listen for sync updates
        EventBus.getDefault().register(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        listAdapter.clickItem(position, getActivity());
    }
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSyncEvent(SyncEvent event) {
        // Update sync status in the list
        listAdapter.notifyDataSetChanged();
    }

}
