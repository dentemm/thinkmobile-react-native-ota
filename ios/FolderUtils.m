#import "FolderUtils.h"

@implementation FolderUtils {}

+ (NSURL *)createOrRetrieveDirectory:(NSURL *)directoryURL {
    NSFileManager *fileManager = [NSFileManager defaultManager];
    BOOL isDirectory;
    BOOL exists = [fileManager fileExistsAtPath:directoryURL.path isDirectory:&isDirectory];
    
    if (!exists || !isDirectory) {
        NSError *error;
        BOOL created = [fileManager createDirectoryAtURL:directoryURL
                             withIntermediateDirectories:YES
                                              attributes:nil
                                                   error:&error];
        if (!created) {
            NSLog(@"FolderUtils: Failed to create directory at %@. Error: %@", directoryURL.path, error);
            return nil;
        }
    }
    
    return directoryURL;
}

+ (NSString *)documentsDirectory {
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    return (paths.count) ? paths[0] : nil;
}

+ (NSURL *)otaDirectory {
    NSURL *otaDirectory = [[NSURL fileURLWithPath:[self documentsDirectory]] URLByAppendingPathComponent:@"ota"];
    NSURL *createdDirectory = [self createOrRetrieveDirectory:otaDirectory];
    return createdDirectory;
}

+ (NSURL *)downloadDestinationDirectory {
    NSURL *downloadDestinationDirectory = [[FolderUtils otaDirectory] URLByAppendingPathComponent:@"download"];
    NSURL *createdDirectory = [self createOrRetrieveDirectory:downloadDestinationDirectory];
    return createdDirectory;
}

+ (NSURL *)destinationDirectoryForVersion:(NSString *)version {
    NSURL *versionDirectory = [[self otaDirectory] URLByAppendingPathComponent:version];
    return [self createOrRetrieveDirectory:versionDirectory];
}

+ (NSURL *)destinationDirectoryForVersion:(NSString *)version fileName:(NSString *)fileName {
    NSURL *versionDirectory = [self destinationDirectoryForVersion:version];
    if (!versionDirectory) {
        NSLog(@"FolderUtils: Failed to get or create version directory for %@", version);
        return nil;
    }
    
    NSURL *fileNameDirectory = [versionDirectory URLByAppendingPathComponent:fileName];
    return [self createOrRetrieveDirectory:fileNameDirectory];
}

+ (NSArray<NSString *> *)getAllFilesInDirectory:(NSURL *)directory {
    NSError *error;
    NSArray<NSString *> *files = [[NSFileManager defaultManager] contentsOfDirectoryAtPath:directory.path error:&error];
    if (error) {
        NSLog(@"FolderUtils: Error getting contents of directory %@: %@", directory, error);
        return @[];
    }
    return files;
}

@end
