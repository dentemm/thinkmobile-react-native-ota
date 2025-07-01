# React Native OTA Library

A React Native library for handling Over-The-Air (OTA) updates in your application.

## Installation

```bash
npm install thinkmobile-react-native-ota
# or
yarn add thinkmobile-react-native-ota
```

## Usage

### Option 1: Configure with props (Recommended)

Wrap your app with the OTAProvider and pass configuration directly:

```typescript
import { OTAProvider } from 'thinkmobile-react-native-ota';

function App() {
  return (
    <OTAProvider
      updateCheckUrl="https://your-api-url.com/check-for-update"
      apiKey="your-api-key"
    >
      <YourApp />
    </OTAProvider>
  );
}
```

Alternatively, you can pass a config object:

```typescript
import { OTAProvider } from 'thinkmobile-react-native-ota';

function App() {
  const otaConfig = {
    updateCheckUrl: 'https://your-api-url.com/check-for-update',
    apiKey: 'your-api-key'
  };

  return (
    <OTAProvider config={otaConfig}>
      <YourApp />
    </OTAProvider>
  );
}
```

## Features

- OTA updates management
- Configuration through React Context or props
- Progress tracking
- Automatic update installation
- Support for both iOS and Android
- Flexible configuration options (props or programmatic)

## License

MIT