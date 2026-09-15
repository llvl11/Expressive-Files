package com.baiel.expressivefiles

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.baiel.expressivefiles.util.createSharedImageLoader

class FilesApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return createSharedImageLoader(this)
    }
}
