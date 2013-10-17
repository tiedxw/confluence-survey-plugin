package org.hivesoft.confluence.admin.representations;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Config {

    @XmlElement
    private String iconSet;

    public String getIconSet() {
        return iconSet;
    }

    public void setIconSet(String iconSet) {
        this.iconSet = iconSet;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Config))
            return false;
        return (((Config) obj).getIconSet().equals(this.iconSet));
    }
}