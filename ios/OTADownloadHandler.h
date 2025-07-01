#import <Foundation/Foundation.h>

@interface OTADownloadHandler : NSObject <NSURLConnectionDelegate>

@property (nonatomic, strong) NSOutputStream *outputFileStream;
@property (nonatomic, assign) long long receivedContentLength;
@property (nonatomic, assign) long long expectedContentLength;
@property (nonatomic, strong) dispatch_queue_t operationQueue;
@property (nonatomic, copy) void (^progressCallback)(float);
@property (nonatomic, copy) void (^doneCallback)(BOOL);
@property (nonatomic, copy) void (^failCallback)(NSError *err);
@property (nonatomic, strong) NSString *downloadUrl;

- (id)init:(NSString *)downloadFilePath
operationQueue:(dispatch_queue_t)operationQueue
progressCallback:(void (^)(float))progressCallback
doneCallback:(void (^)(BOOL))doneCallback
failCallback:(void (^)(NSError *err))failCallback;

- (void)download:(NSString *)url;

+ (NSString *)baseURL;
+ (void)setBaseURL:(NSString *)baseURL;
+ (void)setApiKey:(NSString *)apiKey;

@end
