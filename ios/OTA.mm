#import "OTA.h"
#import "FolderUtils.h"
#import "OTADownloadHandler.h"

#import <SSZipArchive/SSZipArchive.h>
#import <Foundation/Foundation.h>
#import <React/RCTReloadCommand.h>

@interface OTA ()
@property (nonatomic, strong) NSString *apiKey;
@property (nonatomic, strong) NSString *updateCheckUrl;
@end

@implementation OTA

RCT_EXPORT_MODULE()

-(std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeOTASpecJSI>(params);
}

-(void)getAppVersion:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    NSString *version = [OTA appVersion];
    NSLog(@"OTA: App version is %@", version);
    resolve(version);
}

-(void)initiateUpdate:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    NSLog(@"OTA: Initiating update");

    resolve(@{@"success": @YES});
    return;
}

-(void)reloadBundle {
    NSLog(@"OTA: Reloading JS bundle");
    RCTTriggerReloadCommandListeners(@"ota: Reload");
}

-(void)restartApp {
    NSLog(@"OTA: Restarting app");
    if ([NSThread isMainThread]) {
        [self reloadBundle];
    } else {
        dispatch_sync(dispatch_get_main_queue(), ^{
            [self reloadBundle];
        });
    }
    return;
}

-(void)checkForUpdate:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    NSLog(@"OTA: Checking for update");

    if (_updateCheckUrl == nil) {
        NSLog(@"OTA: Configuration not set. Please call setConfig first.");
        reject(@"CONFIG_ERROR", @"OTA configuration not set. Please call setConfig first.", nil);
        return;
    }

    NSString *currentFileName = [[[OTA bundleURL] lastPathComponent] stringByDeletingPathExtension];
    
    // Use "main" as default if currentFileName is nil
    if (!currentFileName) {
        currentFileName = @"main";
    }
    
    // If this is the initial "main" bundle, construct the proper filename convention
    if ([currentFileName isEqualToString:@"main"]) {
        // Format: <platform>_<bundle>_<version>_<timestamp_hex>
        // Use timestamp 0 (Jan 1 1970) for the initial bundle
        NSString *bundleId = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleIdentifier"];
        currentFileName = [NSString stringWithFormat:@"ios_%@_%@_0", bundleId, [OTA appVersion]];
    }

    NSLog(@"OTA: Current bundle name: %@", currentFileName);

    // Create URL with query parameters
    NSURLComponents *urlComponents = [NSURLComponents componentsWithString:_updateCheckUrl];
    NSMutableArray *queryItems = [NSMutableArray array];
    
    [queryItems addObject:[NSURLQueryItem queryItemWithName:@"filename" value:currentFileName]];
    
    urlComponents.queryItems = queryItems;
    NSURL *requestURL = urlComponents.URL;

    if (!requestURL) {
        reject(@"URL_ERROR", @"Failed to create request URL", nil);
        return;
    }

    // Create the request
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:requestURL
                                                           cachePolicy:NSURLRequestUseProtocolCachePolicy
                                                       timeoutInterval:60.0];
    [request setHTTPMethod:@"GET"];

    // Create data task
    NSURLSession *session = [NSURLSession sharedSession];
    NSURLSessionDataTask *dataTask = [session dataTaskWithRequest:request
                                                completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
        if (error) {
            reject(@"API_ERROR", @"Failed to check for update", error);
            return;
        }

        NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
        if (httpResponse.statusCode != 200) {
            NSLog(@"OTA: API returned non-200 status code: %ld", (long)httpResponse.statusCode);
            reject(@"API_ERROR", [NSString stringWithFormat:@"API returned status code %ld", (long)httpResponse.statusCode], nil);
            return;
        }

        NSError *jsonError;
        NSDictionary *jsonResponse = [NSJSONSerialization JSONObjectWithData:data options:0 error:&jsonError];

        if (jsonError) {
            reject(@"JSON_ERROR", @"Failed to parse API response", jsonError);
            return;
        }

        NSLog(@"OTA: JSON response: %@", jsonResponse);

        BOOL updateAvailable = [jsonResponse[@"updateAvailable"] boolValue];

        if (updateAvailable) {
            NSLog(@"OTA: Update available");

            NSString *signedUrlString = jsonResponse[@"signedUrl"];
            NSString *fileName = jsonResponse[@"filename"];

            if (!fileName || !signedUrlString) {
                NSLog(@"OTA: File name or signed URL is nil");
                reject(@"INVALID_RESPONSE", @"File name or signed URL is nil", nil);
                return;
            } 

            NSURL *signedUrl = [NSURL URLWithString:signedUrlString];
            NSURL *downloadDirectory = [FolderUtils downloadDestinationDirectory];
            NSURL *fileUrl = [[downloadDirectory URLByAppendingPathComponent:fileName] URLByAppendingPathExtension:@"zip"];

            [self downloadPackage:signedUrl.absoluteString destinationPath:fileUrl.path resolve:resolve reject:reject];
        } else {
            NSLog(@"OTA: No update available");
            resolve(@{@"updateAvailable": @(updateAvailable)});
        }        
    }];

    [dataTask resume];
}

-(void)downloadPackage:(NSString *)updatePackageUrl destinationPath:(NSString *)destinationPath resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    NSLog(@"OTA: Downloading package from %@ to %@", updatePackageUrl, destinationPath);
    
    dispatch_queue_t operationQueue = dispatch_queue_create("OTADownloadQueue", DISPATCH_QUEUE_SERIAL);
    OTADownloadHandler *downloadHandler = [[OTADownloadHandler alloc]
        init:destinationPath
        operationQueue:operationQueue
        progressCallback:^(float progress) {
            NSLog(@"OTA: Download progress: %.2f%%", progress);
        }
        doneCallback:^(BOOL isZip) {
            NSLog(@"OTA: Download completed, isZip: %@", isZip ? @"YES" : @"NO");

            // Get last part of destination path, and omit extension
            NSString *fileName = [[destinationPath lastPathComponent] stringByDeletingPathExtension];

            NSURL *destinationFolder = [FolderUtils destinationDirectoryForVersion:[OTA appVersion]];
            NSURL *destination = [destinationFolder URLByAppendingPathComponent:fileName];

            [self unzipFileAtPath:destinationPath toDestination:destination.path completionHandler:^(NSError *unzipError) {
                if (unzipError) {
                    NSLog(@"OTA: Unzip failed. Error: %@", unzipError);
                    reject(@"UNZIP_ERROR", @"Failed to unzip update", unzipError);
                } else {
                    NSLog(@"OTA: Unzip completed successfully");
                    [self removeFileAtURL:[NSURL fileURLWithPath:destinationPath]];
                    NSLog(@"OTA: Update process completed successfully");
                    resolve(@{@"success": @YES});
                }
            }];
        }
        failCallback:^(NSError *err) {
            NSLog(@"OTA: Download failed with error: %@", err);
            reject(@"DOWNLOAD_ERROR", @"Failed to download update", err);
        }
    ];
    
    [downloadHandler download:updatePackageUrl];
}

-(void)unzipFileAtPath:(NSString *)zipPath toDestination:(NSString *)destinationPath completionHandler:(void (^)(NSError *error))completionHandler {
    NSLog(@"OTA: Starting unzip process from %@ to %@", zipPath, destinationPath);
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        NSError *error;
        BOOL success = [SSZipArchive unzipFileAtPath:zipPath toDestination:destinationPath overwrite:YES password:nil error:&error];
        dispatch_async(dispatch_get_main_queue(), ^{
            if (success) {
                NSLog(@"OTA: Unzip completed");
            } else {
                NSLog(@"OTA: Unzip failed with error: %@", error);
            }
            completionHandler(error);
        });
    });
}

-(void)removeFileAtURL:(NSURL *)fileURL {
    NSLog(@"OTA: Attempting to remove file at %@", fileURL);
    NSError *error;
    BOOL success = [[NSFileManager defaultManager] removeItemAtURL:fileURL error:&error];
    if (success) {
        NSLog(@"OTA: File removed successfully");
    } else {
        NSLog(@"OTA: Failed to remove file at %@: %@", fileURL, error);
    }
}

+(NSString *)appVersion {
    return [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleShortVersionString"];
}

+(NSURL *)bundleURL {
    NSString *appVersion = [OTA appVersion];
    NSLog(@"OTA: Current app version is %@", appVersion);

    NSURL *otaDirectory = [FolderUtils otaDirectory];
    if (!otaDirectory) {
        NSLog(@"OTA: Failed to get OTA directory, using main bundle");
        return [[NSBundle mainBundle] URLForResource:@"main" withExtension:@"jsbundle"];
    }

    // Clean up outdated versions using our new method
    OTA *instance = [OTA new];
    [instance cleanupStorage:^(NSDictionary *result) {
        NSLog(@"OTA: Cleaned up %@ items during bundle URL check", result[@"deletedCount"]);
    } reject:^(NSString *code, NSString *message, NSError *error) {
        NSLog(@"OTA: Cleanup failed during bundle URL check: %@", message);
    }];

    NSURL *versionDirectory = [FolderUtils destinationDirectoryForVersion:appVersion];
    NSArray *contents = [[NSFileManager defaultManager] contentsOfDirectoryAtURL:versionDirectory
                                                    includingPropertiesForKeys:@[NSURLIsDirectoryKey]
                                                                       options:NSDirectoryEnumerationSkipsHiddenFiles
                                                                         error:nil];
    
    unsigned long long mostRecentTimestamp = 0;
    NSURL *activeBundle = nil;

    for (NSURL *item in contents) {
        NSString *hexTimeStamp = [[item lastPathComponent] componentsSeparatedByString:@"_"].lastObject;
        unsigned long long hexTimeStampValue = strtoull([hexTimeStamp UTF8String], NULL, 16);

        if (hexTimeStampValue > mostRecentTimestamp) {
            mostRecentTimestamp = hexTimeStampValue;
            activeBundle = item;
        }
    }

    NSURL *bundleURL = [activeBundle URLByAppendingPathComponent:@"main.jsbundle"];

    if (bundleURL != nil && [[NSFileManager defaultManager] fileExistsAtPath:bundleURL.path]) {
        NSLog(@"OTA: Using bundle at %@", bundleURL);
        return bundleURL;
    }

    NSLog(@"OTA: Bundle not found in OTA directory, using main bundle");
    return [[NSBundle mainBundle] URLForResource:@"main" withExtension:@"jsbundle"];
}

-(NSString *)getBundleUrl {
    NSURL *bundleURL = [OTA bundleURL];
    NSLog(@"OTA: Bundle URL: %@", bundleURL);
    return bundleURL.absoluteString;
}

-(void)cleanupStorage:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    NSLog(@"OTA: Cleaning up storage");
    
    NSString *appVersion = [OTA appVersion];
    NSURL *otaDirectory = [FolderUtils otaDirectory];
    
    if (!otaDirectory) {
        NSString *errorMessage = @"Failed to get OTA directory";
        NSLog(@"OTA: %@", errorMessage);
        reject(@"DIRECTORY_ERROR", errorMessage, nil);
        return;
    }
    
    NSFileManager *fileManager = [NSFileManager defaultManager];
    NSError *error = nil;
    
    // Get all subdirectories in ota directory
    NSArray *subdirectories = [fileManager contentsOfDirectoryAtURL:otaDirectory
                                       includingPropertiesForKeys:@[NSURLIsDirectoryKey]
                                                          options:NSDirectoryEnumerationSkipsHiddenFiles
                                                            error:&error];
    
    if (error) {
        NSLog(@"OTA: Error getting contents of ota directory: %@", error);
        reject(@"DIRECTORY_ERROR", @"Failed to get directory contents", error);
        return;
    }
    
    NSInteger deletedCount = 0;
    
    // Process all directories
    for (NSURL *subdir in subdirectories) {
        NSString *lastPathComponent = [subdir lastPathComponent];
        if ([lastPathComponent isEqualToString:appVersion]) {
            // Clean up old bundles within current version directory
            deletedCount += [self cleanupOldBundlesInDirectory:subdir];
        } else if (![lastPathComponent isEqualToString:@"download"]) {
            // Remove entire directory for old versions
            NSLog(@"OTA: Removing outdated directory: %@", subdir);
            NSError *deleteError = nil;
            if ([fileManager removeItemAtURL:subdir error:&deleteError]) {
                deletedCount++;
            } else {
                NSLog(@"OTA: Error removing directory %@: %@", subdir, deleteError);
            }
        }
    }
    
    NSLog(@"OTA: Storage cleanup completed. Deleted %ld items", (long)deletedCount);
    resolve(@{
        @"success": @YES,
        @"deletedCount": @(deletedCount)
    });
}

-(NSInteger)cleanupOldBundlesInDirectory:(NSURL *)versionDirectory {
    NSFileManager *fileManager = [NSFileManager defaultManager];
    NSError *error = nil;
    
    NSArray *bundleDirs = [fileManager contentsOfDirectoryAtURL:versionDirectory
                                    includingPropertiesForKeys:@[NSURLIsDirectoryKey]
                                                       options:NSDirectoryEnumerationSkipsHiddenFiles
                                                         error:&error];
    
    if (error || bundleDirs.count <= 1) {
        return 0;
    }
    
    // Find the most recent bundle
    unsigned long long mostRecentTimestamp = 0;
    NSURL *mostRecentBundle = nil;
    
    for (NSURL *bundleDir in bundleDirs) {
        NSString *hexTimeStamp = [[bundleDir lastPathComponent] componentsSeparatedByString:@"_"].lastObject;
        unsigned long long hexTimeStampValue = strtoull([hexTimeStamp UTF8String], NULL, 16);
        
        if (hexTimeStampValue > mostRecentTimestamp) {
            mostRecentTimestamp = hexTimeStampValue;
            mostRecentBundle = bundleDir;
        }
    }
    
    // Delete all bundles except the most recent one
    NSInteger deletedCount = 0;
    for (NSURL *bundleDir in bundleDirs) {
        if (![bundleDir isEqual:mostRecentBundle]) {
            NSLog(@"OTA: Removing old bundle: %@", bundleDir);
            NSError *deleteError = nil;
            if ([fileManager removeItemAtURL:bundleDir error:&deleteError]) {
                deletedCount++;
            } else {
                NSLog(@"OTA: Error removing bundle %@: %@", bundleDir, deleteError);
            }
        }
    }
    
    return deletedCount;
}

RCT_EXPORT_METHOD(setConfig:(NSString *)updateCheckUrl apiKey:(NSString *)apiKey) {
    NSLog(@"OTA: Setting config - updateCheckUrl: %@, apiKey: %@", updateCheckUrl, apiKey);
    _updateCheckUrl = updateCheckUrl;
    _apiKey = apiKey;
}

@end

