from pyspark.ml import Pipeline
from pyspark.ml.classification import LogisticRegression
from pyspark.ml.feature import HashingTF, Tokenizer
from pyspark.sql import Row

import os
import sys
from pyspark.sql.types import *

from pyspark.mllib.classification import LogisticRegressionWithLBFGS
from pyspark.mllib.regression import LabeledPoint
from numpy import array

# Define a type called LabelDocument
LabeledDocument = Row("BuildingID", "SystemInfo", "label")

# Define a function that parses the raw CSV file and returns an object of type LabeledDocument
def parseDocument(line):
    values = [str(x) for x in line.split(',')]
    if (values[3] > values[2]):
        hot = 1.0
    else:
        hot = 0.0

    textValue = str(values[4]) + " " + str(values[5])

    return LabeledDocument((values[6]), textValue, hot)

# Load the raw HVAC.csv file, parse it using the function
data = sc.textFile("/HdiSamples/HdiSamples/SensorSampleData/hvac/HVAC.csv")

documents = data.filter(lambda s: "Date" not in s).map(parseDocument)
training = documents.toDF()

tokenizer = Tokenizer(inputCol="SystemInfo", outputCol="words")
hashingTF = HashingTF(inputCol=tokenizer.getOutputCol(), outputCol="features")
lr = LogisticRegression(maxIter=10, regParam=0.01)
pipeline = Pipeline(stages=[tokenizer, hashingTF, lr])

model = pipeline.fit(training)

training.show()

# SystemInfo here is a combination of system ID followed by system age
Document = Row("id", "SystemInfo")
test = sc.parallelize([("1L", "20 25"),
                ("2L", "4 15"),
                ("3L", "16 9"),
                ("4L", "9 22"),
                ("5L", "17 10"),
                ("6L", "7 22")]) \
    .map(lambda x: Document(*x)).toDF()
# Make predictions on test documents and print columns of interest
prediction = model.transform(test)
selected = prediction.select("SystemInfo", "prediction", "probability")
for row in selected.collect():
    print (row)



