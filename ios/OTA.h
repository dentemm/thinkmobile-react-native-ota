#import <OTASpec/OTASpec.h>
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface OTA: NSObject <NativeOTASpec>

- (void)downloadPackage:(NSString *)updatePackageUrl destinationPath:(NSString *)destinationPath resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
- (void)unzipFileAtPath:(NSString *)zipPath toDestination:(NSString *)destinationPath completionHandler:(void (^)(NSError *error))completionHandler;
- (void)removeFileAtURL:(NSURL *)fileURL;
+ (nullable NSURL *)bundleURL;
- (void)cleanupStorage:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;

@end

NS_ASSUME_NONNULL_END
