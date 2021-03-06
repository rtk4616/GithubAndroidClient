package com.frodo.github.business.repository;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import com.frodo.app.android.ui.fragment.StatedFragment;
import com.frodo.app.framework.toolbox.TextUtils;
import com.frodo.github.R;
import com.frodo.github.bean.dto.response.Content;
import com.frodo.github.bean.dto.response.Issue;
import com.frodo.github.bean.dto.response.Repo;
import com.frodo.github.business.account.AccountModel;
import com.frodo.github.view.CircleProgressDialog;
import com.frodo.github.view.ViewProvider;
import com.mikepenz.octicons_typeface_library.Octicons;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func4;
import rx.schedulers.Schedulers;

/**
 * Created by frodo on 2016/5/7.
 */
public class RepositoryFragment extends StatedFragment<RepositoryView, RepositoryModel> {
	private String repoName;
	private Repo repo;
	private Content content;
	private int closedPullsCount, openedPullsCount, closedIssuesCount, openedIssuesCount;
	private ArrayList<Issue> issues;
	private ArrayList<Issue> pullRequests;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public RepositoryView createUIView(Context context, LayoutInflater inflater, ViewGroup container) {
		return new RepositoryView(this, inflater, container);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.menu_repo, menu);
		updateMenu(menu);
	}

	private void updateMenu(Menu menu) {
		ViewProvider.updateMenuItem(getAndroidContext(), menu, R.id.action_code, Octicons.Icon.oct_code);
		ViewProvider.updateMenuItem(getAndroidContext(), menu, R.id.action_issues, Octicons.Icon.oct_issue_opened);
		ViewProvider.updateMenuItem(getAndroidContext(), menu, R.id.action_pull_requests, Octicons.Icon.oct_git_pull_request);
		ViewProvider.updateMenuItem(getAndroidContext(), menu, R.id.action_pulse, Octicons.Icon.oct_pulse);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onFirstTimeLaunched() {
		Bundle bundle = getArguments();
		if (bundle != null && bundle.containsKey("repo")) {
			repoName = bundle.getString("repo");
			if (repoName != null && repoName.contains("/")) {
				String[] strings = repoName.split("/");
				loadRepositoryWithReactor(strings[0], strings[1]);
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(tag());
	}

	@Override
	public void onSaveState(Bundle outState) {
		if (repo != null)
			outState.putParcelable("repo", repo);
		if (content != null)
			outState.putParcelable("content", content);

		outState.putInt("closedPullsCount", closedPullsCount);
		outState.putInt("openedPullsCount", openedPullsCount);
		outState.putInt("closedIssuesCount", closedIssuesCount);
		outState.putInt("openedIssuesCount", openedIssuesCount);

		if (issues != null && !issues.isEmpty())
			outState.putParcelableArrayList("issues", issues);
		if (pullRequests != null && !pullRequests.isEmpty())
			outState.putParcelableArrayList("pullRequests", pullRequests);
	}

	@Override
	public void onRestoreState(Bundle savedInstanceState) {
		repo = savedInstanceState.getParcelable("repo");
		content = savedInstanceState.getParcelable("content");

		closedPullsCount = savedInstanceState.getInt("closedPullsCount");
		openedPullsCount = savedInstanceState.getInt("openedPullsCount");
		closedIssuesCount = savedInstanceState.getInt("closedIssuesCount");
		openedIssuesCount = savedInstanceState.getInt("openedIssuesCount");

		issues = savedInstanceState.getParcelableArrayList("issues");
		pullRequests = savedInstanceState.getParcelableArrayList("pullRequests");

		getUIView().showDetail(repo);
		getUIView().showReadme(content);
		getUIView().showPulse(closedPullsCount, openedPullsCount, closedIssuesCount, openedIssuesCount);
		getUIView().showIssues(issues);
		getUIView().showPullRequests(pullRequests);
	}

	@Override
	public String tag() {
		return TextUtils.isEmpty(repoName) ? "Repository" : repoName;
	}

	private void loadRepositoryWithReactor(final String ownerName, final String repoName) {
		getModel().loadRepositoryDetailWithReactor(ownerName, repoName)
				.subscribeOn(Schedulers.io())
				.doOnSubscribe(new Action0() {
					@Override
					public void call() {
						getUIView().showEmptyView();
						CircleProgressDialog.showLoadingDialog(getAndroidContext());
					}
				})
				.subscribeOn(AndroidSchedulers.mainThread())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Action1<Repo>() {
					           @Override
					           public void call(Repo repository) {
						           RepositoryFragment.this.repo = repository;
						           if (repository != null) {
							           getUIView().hideEmptyView();
							           getUIView().showDetail(repository);
							           loadMoreInfoWithReactor(repository);
                                   } else {
							           getUIView().showEmptyView();
						           }

						           CircleProgressDialog.hideLoadingDialog();
					           }
				           },
						new Action1<Throwable>() {
							@Override
							public void call(Throwable throwable) {
								CircleProgressDialog.hideLoadingDialog();
								getUIView().showErrorView(ViewProvider.handleError(getMainController().getConfig().isDebug(), throwable));
							}
						});
	}

	private void loadMoreInfoWithReactor(Repo repo) {
		loadReadMeFileWithReactor(repo);
		loadPulseInPastWeekWithReactor(repo);
		loadRecentIssuesWithReactor(repo);
		loadRecentPullRequestsWithReactor(repo);
		AccountModel accountModel = getMainController().getModelFactory()
				.getOrCreateIfAbsent(AccountModel.TAG, AccountModel.class, getMainController());
		getUIView().showNotifications(accountModel.isSignIn(), "");
	}

	private void loadReadMeFileWithReactor(Repo repo) {
		if (!TextUtils.isEmpty(repo.contents_url)) {
			getModel().loadReadmeWithReactor(repo.owner.login, repo.name)
					.observeOn(AndroidSchedulers.mainThread())
					.subscribe(new Action1<Content>() {
						@Override
						public void call(Content content) {
							RepositoryFragment.this.content = content;
							getUIView().showReadme(content);
						}
					}, new Action1<Throwable>() {
						@Override
						public void call(Throwable throwable) {
							throwable.printStackTrace();
						}
					});
		}
	}

	private void loadPulseInPastWeekWithReactor(final Repo repo) {
		Observable<Integer> mergedPullsObservable = getModel().loadMergedPullsInPastWeekWithReactor(repo.owner.login, repo.name);
		Observable<Integer> proposedPullsObservable = getModel().loadProposedPullsInPastWeekWithReactor(repo.owner.login, repo.name);

		Observable<Integer> closedIssuesObservable = getModel().loadClosedIssuesInPastWeekWithReactor(repo.owner.login, repo.name);
		Observable<Integer> openedIssuesObservable = getModel().loadCreatedIssuesInPastWeekWithReactor(repo.owner.login, repo.name);
		Observable.combineLatest(mergedPullsObservable, proposedPullsObservable,
				closedIssuesObservable, openedIssuesObservable,
				new Func4<Integer, Integer, Integer, Integer, Integer[]>() {
					@Override
					public Integer[] call(Integer closedPullsCount, Integer openedPullsCount,
					                      Integer closedIssuesCount, Integer openedIssuesCount) {
						RepositoryFragment.this.closedPullsCount = closedPullsCount;
						RepositoryFragment.this.openedPullsCount = openedPullsCount;
						RepositoryFragment.this.closedIssuesCount = closedIssuesCount;
						RepositoryFragment.this.openedIssuesCount = openedIssuesCount;
						return new Integer[]{closedPullsCount, openedPullsCount, closedIssuesCount, openedIssuesCount};
					}
				}).subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Action1<Integer[]>() {
					@Override
					public void call(Integer[] counts) {
						getUIView().showPulse(counts[0], counts[1], counts[2], counts[3]);
					}
				}, new Action1<Throwable>() {
					@Override
					public void call(Throwable throwable) {
						getUIView().showPulse(0, 0, 0, 0);
					}
				});
	}

	private void loadRecentIssuesWithReactor(Repo repo) {
		getModel().loadRecentIssuesWithReactor(repo.owner.login, repo.name)
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Action1<List<Issue>>() {
					@Override
					public void call(List<Issue> issues) {
						RepositoryFragment.this.issues = (ArrayList<Issue>) issues;
						getUIView().showIssues(issues);
					}
				}, new Action1<Throwable>() {
					@Override
					public void call(Throwable throwable) {
						getUIView().showIssues(null);
						throwable.printStackTrace();
					}
				});
	}

	private void loadRecentPullRequestsWithReactor(Repo repo) {
		getModel().loadRecentPullsWithReactor(repo.owner.login, repo.name)
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Action1<List<Issue>>() {
					@Override
					public void call(List<Issue> pullRequests) {
						RepositoryFragment.this.pullRequests = (ArrayList<Issue>) pullRequests;
						getUIView().showPullRequests(pullRequests);
					}
				}, new Action1<Throwable>() {
					@Override
					public void call(Throwable throwable) {
						getUIView().showPullRequests(null);
						throwable.printStackTrace();
					}
				});
	}
}
