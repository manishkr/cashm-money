import requests
def transfer():
    url = 'http://localhost:9100/v1/coupon/transfer'
    data = {'amount' : 1, "currency_code" : "INR", "payee_mobile" : "+919955154740"}
    headers = {'Content-type': 'application/json', 'Authorization' : 'Bearer 8187baba-dd11-497f-9a11-a0a46b8c76ef'}
    r =requests.post(url, json=data, headers=headers)
    print(r)

from joblib import Parallel, delayed
import multiprocessing

# what are your inputs, and what operation do you want to
# perform on each input. For example...
inputs = range(100)
def processInput(i):
    transfer()
    return i * i

num_cores = multiprocessing.cpu_count()

results = Parallel(n_jobs=num_cores)(delayed(processInput)(i) for i in inputs)
