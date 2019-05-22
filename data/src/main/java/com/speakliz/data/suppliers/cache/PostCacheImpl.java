package com.speakliz.data.suppliers.cache;

import android.content.Context;

import com.speakliz.data.entity.PostEntity;
import com.speakliz.data.suppliers.cache.exception.PostNotFoundException;
import com.speakliz.data.suppliers.cache.serializer.Serializer;
import com.speakliz.data.suppliers.utils.executor.ThreadExecutor;

import java.io.File;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

/**
 * Implementation of interface {@link PostCache} to get data from cache, shared preferences, etc.
 * It is a Singleton
 *
 */

public class PostCacheImpl implements PostCache {


    private static final String SETTINGS_FILE_NAME = "com.speakliz.data.SETTINGS";
    private static final String SETTINGS_KEY_LAST_CACHE_UPDATE = "last_cache_update";

    private static final String DEFAULT_FILE_NAME = "post_";
    private static final long EXPIRATION_TIME = 60 * 10 * 1000;

    private final Context context;
    private final FileManager fileManager;
    private final File cacheDir;
    private final ThreadExecutor threadExecutor;
    private final Serializer serializer;

    private PostCache instance;

    private PostCacheImpl(Context context, FileManager fileManager, File cacheDir, ThreadExecutor threadExecutor, Serializer serializer) {

        if (context == null || serializer == null || fileManager == null || threadExecutor == null) {
            throw new IllegalArgumentException("Invalid null parameter");
        }

        this.context = context;
        this.fileManager = fileManager;
        this.cacheDir = cacheDir;
        this.threadExecutor = threadExecutor;
        this.serializer = serializer;
    }

    /**
     * Get instance Singleton Class
     *
     */
    public PostCache getInstance(Context context, FileManager fileManager, File cacheDir, ThreadExecutor threadExecutor, Serializer serializer){
        if(instance == null){
            instance = new PostCacheImpl(context, fileManager, cacheDir, threadExecutor, serializer);
        }
        return instance;
    }


    @Override
    public Observable<PostEntity> get(int postId) {
        return Observable.create(new ObservableOnSubscribe<PostEntity>() {
            @Override
            public void subscribe(ObservableEmitter<PostEntity> emitter) {

                final File postEntityFile = buildFile(postId);
                final String jsonString = fileManager.readFileContent(postEntityFile);
                final PostEntity postEntity = serializer.deserialize(jsonString, PostEntity.class);

                if(postEntity != null){
                    emitter.onNext(postEntity);
                    emitter.onComplete();
                }else {
                    emitter.onError(new PostNotFoundException());
                }
            }
        });
    }

    @Override
    public void put(PostEntity postEntity) {
        if(postEntity != null){
            final File postEntityFile = this.buildFile(postEntity.getPostId());
            if(!isCached(postEntity.getPostId())){
                final String jsonString = this.serializer.serialize(postEntity, PostEntity.class);
                this.executeAsynchronously(new CacheWriter(this.fileManager, postEntityFile, jsonString));
                setLastCacheUpdateTimeMillis();
            }
        }
    }

    @Override
    public boolean isCached(int postId) {
        final File postEntityFile = this.buildFile(postId);
        return this.fileManager.exists(postEntityFile);
    }

    @Override
    public boolean isExpired() {
        long currentTime = System.currentTimeMillis();
        long lastUpdateTime = this.getLastCacheUpdateTimeMillis();

        boolean expired = ((currentTime -lastUpdateTime) > EXPIRATION_TIME);

        if(expired){
            this.evictAll();;
        }

        return expired;
    }

    @Override
    public void evictAll() {
        this.executeAsynchronously(new CacheEvictor(this.fileManager, this.cacheDir));
    }


    /**
     * Build a file, used to be inserted in the disk cache.
     * StringBuilder is used to create the name of the file stored in cache
     *
     * @param postId The id user to build the file.
     * @return A valid file.
     */
    private File buildFile(int postId) {
        final StringBuilder fileNameBuilder = new StringBuilder();
        fileNameBuilder.append(this.cacheDir.getPath());
        fileNameBuilder.append(File.separator);
        fileNameBuilder.append(DEFAULT_FILE_NAME);
        fileNameBuilder.append(postId);

        return new File(fileNameBuilder.toString());
    }

    /**
     * Set in millis, the last time the cache was accessed.
     */
    private void setLastCacheUpdateTimeMillis() {
        final long currentMillis = System.currentTimeMillis();
        this.fileManager.writeToSharedPreferences(this.context, SETTINGS_FILE_NAME,
                SETTINGS_KEY_LAST_CACHE_UPDATE, currentMillis);
    }

    /**
     * Get in millis, the last time the cache was accessed.
     */
    private long getLastCacheUpdateTimeMillis() {
        return this.fileManager.getFromSharedPreferences(this.context, SETTINGS_FILE_NAME,
                SETTINGS_KEY_LAST_CACHE_UPDATE);
    }

    /**
     * Executes a {@link Runnable} in another Thread.
     *
     * @param runnable {@link Runnable} to execute
     */
    private void executeAsynchronously(Runnable runnable) {
        this.threadExecutor.execute(runnable);
    }

    /**
     * {@link Runnable} class for writing to disk.
     */
    private static class CacheWriter implements Runnable {
        private final FileManager fileManager;
        private final File fileToWrite;
        private final String fileContent;

        CacheWriter(FileManager fileManager, File fileToWrite, String fileContent) {
            this.fileManager = fileManager;
            this.fileToWrite = fileToWrite;
            this.fileContent = fileContent;
        }

        @Override public void run() {
            this.fileManager.writeToFile(fileToWrite, fileContent);
        }
    }

    /**
     * {@link Runnable} class for evicting all the cached files
     */
    private static class CacheEvictor implements Runnable {
        private final FileManager fileManager;
        private final File cacheDir;

        CacheEvictor(FileManager fileManager, File cacheDir) {
            this.fileManager = fileManager;
            this.cacheDir = cacheDir;
        }

        @Override public void run() {
            this.fileManager.clearDirectory(this.cacheDir);
        }
    }

}
