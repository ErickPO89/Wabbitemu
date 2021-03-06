package com.Revsoft.Wabbitemu.fragment;

import java.util.List;

import android.app.Fragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.Revsoft.Wabbitemu.R;
import com.Revsoft.Wabbitemu.utils.AdUtils;
import com.Revsoft.Wabbitemu.utils.BrowseCallback;
import com.Revsoft.Wabbitemu.utils.FileUtils;
import com.Revsoft.Wabbitemu.utils.IntentConstants;
import com.google.android.gms.ads.AdView;

public class BrowseFragment extends Fragment {

	private final FileUtils mFileUtils = FileUtils.getInstance();

	private AsyncTask<Void, Void, ArrayAdapter<String>> mSearchTask;
	private ListView mListView;

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.browse, container, false);

		if (getArguments() != null) {
			final Bundle arguments = getArguments();
			final String extensionsRegex = arguments.getString(IntentConstants.EXTENSION_EXTRA_REGEX);
			final int returnId = arguments.getInt(IntentConstants.RETURN_ID);

			mListView = (ListView) view.findViewById(R.id.browseView);
			mListView.setOnItemClickListener(new OnItemClickListener() {

				@Override
				public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
					final String filePath = (String) mListView.getItemAtPosition(position);
					final BrowseCallback callback = (BrowseCallback) getActivity();
					callback.callback(returnId, filePath);
				}
			});

			startSearch(view, extensionsRegex);

			final AdView adView = (AdView) view.findViewById(R.id.adView4);
			AdUtils.loadAd(getResources(), adView);
		}

		return view;
	}

	private void startSearch(final View view, final String extensionsRegex) {
		mSearchTask = new AsyncTask<Void, Void, ArrayAdapter<String>>() {
			private Context mContext;
			private View mLoadingSpinner;

			@Override
			protected void onPreExecute() {
				mContext = getActivity();
				mLoadingSpinner = view.findViewById(R.id.browseLoadingSpinner);
				mLoadingSpinner.setVisibility(View.VISIBLE);
			}

			@Override
			protected ArrayAdapter<String> doInBackground(final Void... params) {
				final List<String> files = mFileUtils.getValidFiles(extensionsRegex);
				return new ArrayAdapter<String>(mContext, android.R.layout.simple_list_item_1, files);
			}

			@Override
			protected void onPostExecute(final ArrayAdapter<String> adapter) {
				mLoadingSpinner.setVisibility(View.GONE);
				mListView.setAdapter(adapter);
				mSearchTask = null;
			}
		};

		if (extensionsRegex != null) {
			mSearchTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (mSearchTask != null) {
			mSearchTask.cancel(true);
		}
	}
}