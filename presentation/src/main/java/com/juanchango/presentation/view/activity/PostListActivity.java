package com.juanchango.presentation.view.activity;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.juanchango.data.entity.mapper.PostFromEntityMapper;
import com.juanchango.data.executor.JobExecutor;
import com.juanchango.data.repository.PostDataRepository;
import com.juanchango.data.repository.datasource.PostDataSourceFactory;
import com.juanchango.data.suppliers.cache.FileManager;
import com.juanchango.data.suppliers.cache.PostCache;
import com.juanchango.data.suppliers.cache.PostCacheImpl;
import com.juanchango.data.suppliers.cache.serializer.Serializer;
import com.juanchango.domain.executor.PostExecutionThread;
import com.juanchango.domain.executor.ThreadExecutor;
import com.juanchango.domain.interactor.GetPostList;
import com.juanchango.domain.model.PostModel;
import com.juanchango.domain.repository.PostRepository;
import com.juanchango.presentation.R;
import com.juanchango.presentation.UiThread;

import java.io.File;
import java.util.List;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;
import timber.log.Timber;

public class PostListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_list);

//        Timber.plant(new Timber.DebugTree());
        Timber.i("onCreate");

        // Data
        Context context = getApplicationContext();
        FileManager fileManager = new FileManager();
        File cacheDir = getCacheDir();
        ThreadExecutor threadExecutorData = JobExecutor.getInstance();        // Singleton
        Serializer serializer = Serializer.getInstance(); // Singleton

        PostCache postCache = PostCacheImpl.getInstance(context, fileManager, cacheDir, threadExecutorData, serializer);         // Singleton


        // Domain
        PostDataSourceFactory postDataStoreFactory = PostDataSourceFactory.getInstance(context,
                postCache);
        PostFromEntityMapper postEntityDataMapper = PostFromEntityMapper.getInstance(); // Singleton
        PostRepository postRepository = PostDataRepository.getInstance(postDataStoreFactory, postEntityDataMapper); // Singleton


        // Presentation
        ThreadExecutor threadExecutorDomain = JobExecutor.getInstance(); // Singleton
        PostExecutionThread uiThread = UiThread.getInstance();  // Singleton

        CompositeDisposable compositeDisposable = new CompositeDisposable();

        getPostList = new GetPostList(postRepository, threadExecutorDomain, uiThread, compositeDisposable);


    }

    GetPostList getPostList;

    @Override
    protected void onResume() {
        super.onResume();
        Timber.i("onResume");

        DisposableObserver<List<PostModel>> listDisposableObserver = new DisposableObserver<List<PostModel>>() {
            @Override
            public void onNext(List<PostModel> posts) {
                Timber.i("onNext");

                for (PostModel post:posts){
                    Timber.i("PostModel # %d = %s", post.getPostId(), post.getTitle());
                }

            }

            @Override
            public void onError(Throwable e) {
                Timber.i("onError %s", e.toString());
            }

            @Override
            public void onComplete() {
                Timber.i("onComplete");
            }
        };

        getPostList.execute(listDisposableObserver, null);

    }


}
