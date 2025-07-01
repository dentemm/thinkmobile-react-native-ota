import React, { createContext, useContext, useState, ReactNode, useEffect } from 'react';
import NativeOTA from './NativeOTA';

interface OTAConfig {
  updateCheckUrl: string;
  apiKey: string;
}

interface OTAContextType {
  config: OTAConfig | null;
  setConfig: (config: OTAConfig) => void;
  isConfigured: boolean;
}

const OTAContext = createContext<OTAContextType | undefined>(undefined);

interface OTAProviderProps {
  children: ReactNode;
  updateCheckUrl?: string;
  apiKey?: string;
  config?: OTAConfig;
}

export const OTAProvider: React.FC<OTAProviderProps> = ({ 
  children, 
  updateCheckUrl, 
  apiKey, 
  config: propConfig 
}) => {
  const [config, setConfigState] = useState<OTAConfig | null>(null);

  // Initialize config from props
  useEffect(() => {
    let initialConfig: OTAConfig | null = null;

    // Priority: explicit config prop > individual props
    if (propConfig) {
      initialConfig = propConfig;
    } else if (updateCheckUrl && apiKey) {
      initialConfig = { updateCheckUrl, apiKey };
    }

    if (initialConfig) {
      setConfigState(initialConfig);
      NativeOTA.setConfig(initialConfig.updateCheckUrl, initialConfig.apiKey);
    }
  }, [propConfig, updateCheckUrl, apiKey]);

  const setConfig = (newConfig: OTAConfig) => {
    setConfigState(newConfig);
    NativeOTA.setConfig(newConfig.updateCheckUrl, newConfig.apiKey);
  };

  const isConfigured = config !== null;

  return (
    <OTAContext.Provider value={{ config, setConfig, isConfigured }}>
      {children}
    </OTAContext.Provider>
  );
};
