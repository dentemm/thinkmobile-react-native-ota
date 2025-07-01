#import "OTADownloadHandler.h"

@implementation OTADownloadHandler {
    char _header[4];
}

static NSString *_baseURL = nil;
static NSString *_apiKey = nil;

- (id)init:(NSString *)downloadFilePath
operationQueue:(dispatch_queue_t)operationQueue
progressCallback:(void (^)(float))progressCallback
doneCallback:(void (^)(BOOL))doneCallback
failCallback:(void (^)(NSError *err))failCallback {
    NSLog(@"OTADownloadHandler: Initializing with file path: %@", downloadFilePath);
    self.outputFileStream = [NSOutputStream outputStreamToFileAtPath:downloadFilePath append:NO];
    self.receivedContentLength = 0;
    self.operationQueue = operationQueue;
    self.progressCallback = progressCallback;
    self.doneCallback = doneCallback;
    self.failCallback = failCallback;
    return self;
}

- (void)download:(NSString *)url {
    NSLog(@"OTADownloadHandler: Starting download from URL: %@", url);
    self.downloadUrl = url;
    NSURLRequest *request = [NSURLRequest requestWithURL:[NSURL URLWithString:url]
                                             cachePolicy:NSURLRequestUseProtocolCachePolicy
                                         timeoutInterval:60.0];
    NSURLConnection *connection = [[NSURLConnection alloc] initWithRequest:request
                                                                  delegate:self
                                                          startImmediately:NO];
    if ([NSOperationQueue instancesRespondToSelector:@selector(setUnderlyingQueue:)]) {
        NSLog(@"OTADownloadHandler: Using custom operation queue");
        NSOperationQueue *delegateQueue = [NSOperationQueue new];
        delegateQueue.underlyingQueue = self.operationQueue;
        [connection setDelegateQueue:delegateQueue];
    } else {
        NSLog(@"OTADownloadHandler: Using main run loop");
        [connection scheduleInRunLoop:[NSRunLoop mainRunLoop]
                              forMode:NSDefaultRunLoopMode];
    }
    [connection start];
}

#pragma mark NSURLConnection Delegate Methods

- (NSCachedURLResponse *)connection:(NSURLConnection *)connection willCacheResponse:(NSCachedURLResponse*)cachedResponse {
    NSLog(@"OTADownloadHandler: Preventing caching of response");
    return nil;
}

- (void)connection:(NSURLConnection *)connection didReceiveResponse:(NSURLResponse *)response {
    NSLog(@"OTADownloadHandler: Received response");
    if ([response isKindOfClass:[NSHTTPURLResponse class]]) {
        NSInteger statusCode = [(NSHTTPURLResponse *)response statusCode];
        if (statusCode >= 400) {
            NSLog(@"OTADownloadHandler: Received error status code: %ld", (long)statusCode);
            [self.outputFileStream close];
            [connection cancel];
            NSError *err = [NSError errorWithDomain:@"OTADownloadHandler" code:statusCode userInfo:@{NSLocalizedDescriptionKey: [NSString stringWithFormat:@"Received %ld response from %@", (long)statusCode, self.downloadUrl]}];
            self.failCallback(err);
            return;
        }
    }
    self.expectedContentLength = response.expectedContentLength;
    NSLog(@"OTADownloadHandler: Expected content length: %lld", self.expectedContentLength);
    [self.outputFileStream open];
}

- (void)connection:(NSURLConnection *)connection didReceiveData:(NSData *)data {
    if (self.receivedContentLength < 4) {
        [self checkFileHeader:data];
    }
    
    self.receivedContentLength = self.receivedContentLength + [data length];
    NSInteger bytesLeft = [data length];
    do {
        NSInteger bytesWritten = [self.outputFileStream write:(const uint8_t *)[data bytes] maxLength:bytesLeft];
        if (bytesWritten == -1) {
            NSLog(@"OTADownloadHandler: Error writing to file");
            break;
        }
        bytesLeft -= bytesWritten;
    } while (bytesLeft > 0);
    
    if (self.expectedContentLength > 0) {
        float progress = ((float)self.receivedContentLength / (float)self.expectedContentLength) * 100.0;
        self.progressCallback(progress);
    } 
    
    if (bytesLeft) {
        NSLog(@"OTADownloadHandler: Failed to write entire data chunk");
        [self.outputFileStream close];
        [connection cancel];
        self.failCallback([self.outputFileStream streamError]);
    }
}

- (void)connection:(NSURLConnection*)connection didFailWithError:(NSError*)error {
    NSLog(@"OTADownloadHandler: Connection failed with error: %@", error);
    [self.outputFileStream close];
    self.failCallback(error);
}

- (void)connectionDidFinishLoading:(NSURLConnection *)connection {
    NSLog(@"OTADownloadHandler: Connection finished loading");
    [self.outputFileStream close];
    if (self.receivedContentLength < 1) {
        NSLog(@"OTADownloadHandler: Received empty response");
        NSError *err = [NSError errorWithDomain:@"OTADownloadHandler" code:0 userInfo:@{NSLocalizedDescriptionKey: [NSString stringWithFormat:@"Received empty response from %@", self.downloadUrl]}];
        self.failCallback(err);
        return;
    }
    
    if (self.expectedContentLength > 0) {
        NSLog(@"OTADownloadHandler: Verifying received content length");
        NSAssert(self.receivedContentLength == self.expectedContentLength, @"Received content length does not match expected content length");
    }
    
    BOOL isZip = _header[0] == 'P' && _header[1] == 'K' && _header[2] == 3 && _header[3] == 4;
    NSLog(@"OTADownloadHandler: File is zip: %@", isZip ? @"YES" : @"NO");
    self.doneCallback(isZip);
}

- (void)checkFileHeader:(NSData *)data {
    NSLog(@"OTADownloadHandler: Checking file header");
    for (int i = 0; i < [data length]; i++) {
        int headerOffset = (int)self.receivedContentLength + i;
        if (headerOffset >= 4) {
            break;
        }
        const uint8_t *bytes = (const uint8_t *)[data bytes];
        _header[headerOffset] = bytes[i];
    }
}

+ (NSString *)baseURL {
    if (_baseURL == nil) {
        NSLog(@"OTADownloadHandler: Base URL not set. Please call setConfig first.");
        @throw [NSException exceptionWithName:@"IllegalStateException"
                                     reason:@"Base URL not set. Please call setConfig first."
                                   userInfo:nil];
    }
    return _baseURL;
}

+ (void)setBaseURL:(NSString *)baseURL {
    NSLog(@"OTADownloadHandler: Setting base URL to: %@", baseURL);
    _baseURL = baseURL;
}

+ (void)setApiKey:(NSString *)apiKey {
    NSLog(@"OTADownloadHandler: Setting API key");
    _apiKey = apiKey;
}

@end
