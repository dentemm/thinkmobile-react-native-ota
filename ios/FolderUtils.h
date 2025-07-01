@interface FolderUtils: NSObject

+ (NSURL *)otaDirectory;

+ (nullable NSURL *)downloadDestinationDirectory;

+ (nullable NSURL *)destinationDirectoryForVersion:(NSString *)version;

+ (nullable NSURL *)destinationDirectoryForVersion:(NSString *)version fileName:(NSString *)fileName;

+ (NSArray<NSString *> *)getAllFilesInDirectory:(NSURL *)directory;

@end
