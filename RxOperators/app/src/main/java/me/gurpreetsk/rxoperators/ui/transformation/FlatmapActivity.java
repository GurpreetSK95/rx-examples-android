package me.gurpreetsk.rxoperators.ui.transformation;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import me.gurpreetsk.rxoperators.R;
import me.gurpreetsk.rxoperators.model.github.GithubResults;
import me.gurpreetsk.rxoperators.model.github.GithubUser;
import me.gurpreetsk.rxoperators.model.github.Project;
import me.gurpreetsk.rxoperators.rest.ApiClient;
import me.gurpreetsk.rxoperators.rest.ApiInterface;
import me.gurpreetsk.rxoperators.util.SeeLink;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FlatmapActivity extends AppCompatActivity {

  @BindView(R.id.textview_flatmap)
  TextView textviewFlatmap;

  ApiInterface apiService;

  private static final String TAG = FlatmapActivity.class.getSimpleName();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_flatmap);
    ButterKnife.bind(this);
    setTitle(TAG);

    apiService =
        ApiClient.getClient().create(ApiInterface.class);

  }

  @Override
  protected void onStart() {
    super.onStart();
    exampleWork();
  }

  /**
   * Here we're trying to request some data first, and then request some meta data
   * The challenge is to use only ONE subscribe call
   * This can be done with flatmap operator
   */
  @SeeLink(links = {"https://stackoverflow.com/questions/22847105/when-do-you-use-map-vs-flatmap-in-rxjava"})
  private void exampleWork() {
    apiService.getGithubRepos("rxjava")
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io())
        // the FlatMap operator does some processing on each element passed by the observable to it
        // and returns an *observable* for each element
        .flatMap(new Function<Response<GithubResults>, ObservableSource<Project>>() {
          @Override
          public ObservableSource<Project> apply(@NonNull Response<GithubResults> githubResultsResponse) throws Exception {
            return Observable.fromIterable(githubResultsResponse.body().getItems());
          }
        })
        .flatMap(new Function<Project, ObservableSource<GithubUser>>() {
          @Override
          public Observable<GithubUser> apply(@NonNull Project project) throws Exception {
            return apiService.getUserInfo(project.getOwner().getLogin());
          }
        })
        //take only first 10 users
        .take(10)
        .retry(5)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Observer<GithubUser>() {
          Disposable d;

          @Override
          public void onSubscribe(@NonNull Disposable d) {
            this.d = d;
          }

          @Override
          public void onNext(@NonNull GithubUser user) {
            Log.d(TAG, "apply: " + user.getLogin());
            textviewFlatmap.setText(textviewFlatmap.getText() + "\n" + user.getLogin());
          }

          @Override
          public void onError(@NonNull Throwable e) {
            Toast.makeText(FlatmapActivity.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "onError: ", e);
          }

          @Override
          public void onComplete() {
            if (!d.isDisposed())
              d.dispose();
          }
        });
  }

  public void callRetrofitNormally() {

    apiService.callGithubRepos("rxjava").enqueue(new Callback<GithubResults>() {
      @Override
      public void onResponse(Call<GithubResults> call, Response<GithubResults> response) {
        if (response.code() == 200) {
          for (Project project : response.body().getItems()) {
            apiService.callUserInfo(project.getOwner().getLogin()).enqueue(new Callback<GithubUser>() {
              @Override
              public void onResponse(Call<GithubUser> call, Response<GithubUser> response) {
                if (response.code() == 200) {
                  Log.i(TAG, "onResponse: " + response.body().getName());
                }
              }

              @Override
              public void onFailure(Call<GithubUser> call, Throwable t) {
                Log.e(TAG, "onFailure: ", t);
              }
            });
          }
        }
      }

      @Override
      public void onFailure(Call<GithubResults> call, Throwable t) {
        Log.e(TAG, "onFailure: ", t);
      }
    });
    // how'll you successfully retry? repeat? handle errors? take only n items?
    // save yourself from callback hell?
    // if its something like file handling(no retrofit required), how'll you manage UI updates?
  }

}
